package io.github.shardkiht.rentdetective.app.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.agent.loop.ReActAgentLoop;
import io.github.shardkiht.rentdetective.agent.report.EvidenceChainReport;
import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.app.mapper.ListingMapper;
import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.ChatResponse;
import io.github.shardkiht.rentdetective.llm.Message;
import io.github.shardkiht.rentdetective.llm.api.LLMClient;
import io.github.shardkiht.rentdetective.semantic.engine.EngineResult;
import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import io.github.shardkiht.rentdetective.semantic.engine.RuleEngine;
import io.github.shardkiht.rentdetective.semantic.pricing.PriceExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 三方案对比评测服务。
 * 从 listings 表读取 104 条标注数据，分别用三种策略跑批，按 evalGroup 分组统计准确率。
 *
 * 策略 A：纯规则引擎（无 LLM 调用，确定性打分）
 * 策略 B：纯 LLM（单次调用，无工具、无多步推理）
 * 策略 C：Agent + RAG（完整 ReAct 循环 + 工具 + 向量检索）
 */
@Service
public class ComparisonEvalService {

    private static final Logger log = LoggerFactory.getLogger(ComparisonEvalService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 异步评测状态 */
    public enum Status { IDLE, RUNNING, COMPLETED, FAILED }

    public record EvalProgress(Status status, int processed, int total, ComparisonReport result, String error) {}

    private final ConcurrentHashMap<String, EvalProgress> progressMap = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "eval-worker");
        t.setDaemon(true);
        return t;
    });

    private static final String LLM_EVAL_PROMPT = """
            你是一个租房风险判断专家。根据以下房源信息，判断其风险等级。
            只输出一个 JSON 对象，不要输出其他任何文字：
            {"verdict": "五选一", "confidence": 0到1之间的数字}
            
            verdict 只能是以下五个值之一：
            - SAFE：信息具体真实、价格合理、有可验证细节，无风险
            - SUSPICIOUS：存在明显欺诈特征（话术套路、信息矛盾、价格异常、中介伪装）
            - REVIEW：有疑点但证据不充分，建议人工复核
            - INSUFFICIENT：关键信息严重缺失（无价格/无位置/无描述），无法做出有效判断
            - NOT_LISTING：内容根本不是房源信息（广告、招聘、无关内容等）
            
            判断标准：
            - 只要标题或描述中有具体小区名、具体价格、具体位置，就不能判 INSUFFICIENT
            - 标题是"找室友""求租"这类不是出租房源的，判 NOT_LISTING
            - 描述中有微信号/电话引流、大量房源汇总、价格明显低于市场价的，判 SUSPICIOUS
            - 信息完整但无明显问题的，判 SAFE
            
            示例：
            标题: 余杭区草荡苑小区，房东直租地铁19号线海创园站
            描述: 三室两厅，朝南带阳台，民用水电，房东直租无中介费
            价格: 4500 元/月
            位置: 余杭区
            → {"verdict": "SAFE", "confidence": 0.9}
            
            标题: 杭州无中介费租房、2号线5号线、大量好房!
            描述: 加微信看房，房源多多，价格优惠
            价格: 1500 元/月
            位置: 杭州
            → {"verdict": "SUSPICIOUS", "confidence": 0.85}
            
            标题: 杭州上城找室友
            描述: 本人女，想在上城区找合租室友
            价格: 未知 元/月
            位置: 上城区
            → {"verdict": "NOT_LISTING", "confidence": 0.9}
            
            房源信息：
            标题: %s
            描述: %s
            价格: %s 元/月
            位置: %s
            """;

    private final ListingMapper listingMapper;
    private final RuleEngine ruleEngine;
    private final PriceExtractor priceExtractor;
    private final LLMClient llmClient;
    private final ReActAgentLoop agentLoop;

    public ComparisonEvalService(ListingMapper listingMapper,
                                 RuleEngine ruleEngine,
                                 PriceExtractor priceExtractor,
                                 LLMClient llmClient,
                                 ReActAgentLoop agentLoop) {
        this.listingMapper = listingMapper;
        this.ruleEngine = ruleEngine;
        this.priceExtractor = priceExtractor;
        this.llmClient = llmClient;
        this.agentLoop = agentLoop;
    }

    /**
     * 异步启动评测（立即返回，后台执行）。
     */
    public void startAsync(String strategy) {
        EvalProgress current = progressMap.get(strategy);
        if (current != null && current.status() == Status.RUNNING) {
            log.warn("[{}] 评测已在运行中，跳过重复触发", strategy);
            return;
        }
        progressMap.put(strategy, new EvalProgress(Status.RUNNING, 0, 0, null, null));
        executor.submit(() -> {
            try {
                ComparisonReport report = run(strategy);
                progressMap.put(strategy, new EvalProgress(Status.COMPLETED, report.total(), report.total(), report, null));
            } catch (Exception e) {
                log.error("[{}] 评测异常终止", strategy, e);
                progressMap.put(strategy, new EvalProgress(Status.FAILED, 0, 0, null, e.getMessage()));
            }
        });
    }

    /**
     * 查询评测进度/结果。
     */
    public EvalProgress getProgress(String strategy) {
        return progressMap.getOrDefault(strategy, new EvalProgress(Status.IDLE, 0, 0, null, null));
    }

    /**
     * 运行指定策略的评测（同步，供内部调用）。
     *
     * @param strategy "rule" / "llm" / "agent"
     * @return 按 evalGroup 分组的评测结果
     */
    public ComparisonReport run(String strategy) {
        List<Listing> listings = listingMapper.selectList(null);
        log.info("开始评测 [{}]，共 {} 条数据", strategy, listings.size());
        progressMap.put(strategy, new EvalProgress(Status.RUNNING, 0, listings.size(), null, null));

        Map<String, GroupResult> groups = new LinkedHashMap<>();
        groups.put("normal", new GroupResult());
        groups.put("info_insufficient", new GroupResult());
        groups.put("not_listing", new GroupResult());

        int processed = 0;
        for (Listing listing : listings) {
            processed++;
            String group = listing.getEvalGroup() != null ? listing.getEvalGroup() : "normal";
            GroupResult gr = groups.getOrDefault(group, groups.get("normal"));

            String predicted;
            try {
                predicted = switch (strategy) {
                    case "rule" -> evalByRule(listing);
                    case "llm" -> evalByLLM(listing);
                    case "agent" -> evalByAgent(listing);
                    default -> throw new IllegalArgumentException("未知策略: " + strategy);
                };
            } catch (Exception e) {
                log.warn("[{}] #{} 评测异常: {}", strategy, listing.getId(), e.getMessage());
                predicted = "ERROR";
            }

            boolean correct = judgeCorrect(group, listing.getRiskLevel(), predicted);
            gr.total++;
            if (correct) {
                gr.correct++;
            } else {
                gr.misCases.add(new ComparisonReport.MisCase(
                        listing.getId().intValue(),
                        listing.getRiskLevel(),
                        predicted,
                        truncate(listing.getTitle(), 40)
                ));
            }

            if (processed % 10 == 0) {
                log.info("[{}] 进度 {}/{}", strategy, processed, listings.size());
                progressMap.put(strategy, new EvalProgress(Status.RUNNING, processed, listings.size(), null, null));
            }
        }

        log.info("[{}] 评测完成", strategy);
        return buildReport(strategy, groups);
    }

    // ==================== 三种策略实现 ====================

    private String evalByRule(Listing listing) {
        ListingContext ctx = ListingContext.fromListing(listing, priceExtractor);
        EngineResult result = ruleEngine.evaluate(ctx);
        return result.verdict().name();
    }

    private String evalByLLM(Listing listing) {
        String prompt = String.format(LLM_EVAL_PROMPT,
                nullSafe(listing.getTitle()),
                nullSafe(listing.getDescription()),
                listing.getPrice() != null ? String.valueOf(listing.getPrice().intValue()) : "未知",
                nullSafe(listing.getLocation()));

        ChatResponse response = llmClient.chat(
                new ChatRequest(List.of(Message.user(prompt)), null, 0.1));

        return parseVerdictFromLLM(response.content());
    }

    private String evalByAgent(Listing listing) {
        EvidenceChainReport report = agentLoop.investigate(listing);
        return report.getVerdict() != null ? report.getVerdict() : "UNKNOWN";
    }

    // ==================== 判定逻辑 ====================

    /**
     * 判断预测是否正确。
     * normal 组：predicted 与 humanLabel 匹配（safe↔SAFE, suspicious↔SUSPICIOUS）
     * info_insufficient 组：predicted ∈ {INSUFFICIENT, REVIEW, SUSPICIOUS}（标出有问题即可）
     * not_listing 组：predicted == NOT_LISTING
     */
    private boolean judgeCorrect(String group, String humanLabel, String predicted) {
        if (predicted == null || "ERROR".equals(predicted)) {
            return false;
        }
        String pred = predicted.toUpperCase();

        return switch (group) {
            case "normal" -> {
                String label = humanLabel != null ? humanLabel.toLowerCase() : "";
                yield switch (label) {
                    case "safe" -> "SAFE".equals(pred);
                    case "suspicious" -> "SUSPICIOUS".equals(pred) || "REVIEW".equals(pred);
                    default -> false;
                };
            }
            case "info_insufficient" ->
                    "INSUFFICIENT".equals(pred) || "REVIEW".equals(pred) || "SUSPICIOUS".equals(pred);
            case "not_listing" -> "NOT_LISTING".equals(pred);
            default -> false;
        };
    }

    // ==================== 辅助方法 ====================

    private String parseVerdictFromLLM(String content) {
        try {
            // 尝试从返回文本中提取 JSON
            String json = content.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            JsonNode node = MAPPER.readTree(json);
            JsonNode verdictNode = node.get("verdict");
            if (verdictNode != null) {
                return verdictNode.asText().toUpperCase();
            }
        } catch (Exception ignored) {
        }
        // 兜底：文本里包含关键词（按特异性从高到低匹配）
        String upper = content.toUpperCase();
        if (upper.contains("NOT_LISTING") || upper.contains("NOT LISTING")) return "NOT_LISTING";
        if (upper.contains("INSUFFICIENT")) return "INSUFFICIENT";
        if (upper.contains("SUSPICIOUS")) return "SUSPICIOUS";
        if (upper.contains("REVIEW")) return "REVIEW";
        if (upper.contains("SAFE")) return "SAFE";
        return "UNKNOWN";
    }

    private ComparisonReport buildReport(String strategy, Map<String, GroupResult> groups) {
        List<ComparisonReport.GroupReport> groupReports = new ArrayList<>();
        int totalAll = 0, correctAll = 0;

        for (var entry : groups.entrySet()) {
            GroupResult gr = entry.getValue();
            if (gr.total == 0) continue;
            double accuracy = (double) gr.correct / gr.total;
            groupReports.add(new ComparisonReport.GroupReport(
                    entry.getKey(), gr.total, gr.correct, accuracy, gr.misCases));
            totalAll += gr.total;
            correctAll += gr.correct;
        }

        double overallAccuracy = totalAll > 0 ? (double) correctAll / totalAll : 0;
        return new ComparisonReport(strategy, totalAll, correctAll, overallAccuracy, groupReports);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static class GroupResult {
        int total = 0;
        int correct = 0;
        List<ComparisonReport.MisCase> misCases = new ArrayList<>();
    }
}
