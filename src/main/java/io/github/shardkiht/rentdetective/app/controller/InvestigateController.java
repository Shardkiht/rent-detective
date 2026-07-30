package io.github.shardkiht.rentdetective.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.agent.report.EvidenceChainReport;
import io.github.shardkiht.rentdetective.domain.entity.Listing;
import io.github.shardkiht.rentdetective.app.service.InvestigationService;
import io.github.shardkiht.rentdetective.rag.CaseVectorService;
import io.github.shardkiht.rentdetective.rag.SimilarCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 房源调查 SSE 接口。
 * Agent 循环手写，工具可插拔；识坑规则来自人工标注，在 rules 包。
 */
@RestController
@RequestMapping("/api/investigate")
public class InvestigateController {

    private static final Logger log = LoggerFactory.getLogger(InvestigateController.class);

    private final InvestigationService investigationService;
    private final CaseVectorService caseVectorService;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    public InvestigateController(InvestigationService investigationService,
                                 CaseVectorService caseVectorService) {
        this.investigationService = investigationService;
        this.caseVectorService = caseVectorService;
        this.mapper = new ObjectMapper();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "investigate-sse");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * POST /api/investigate — SSE 流式返回 Agent 调查轨迹与最终报告。
     * 请求体：{"title":"...","description":"...","price":1500,"phone":"..."}
     * <p>
     * SSE 事件：
     * - step:   每个 AgentStep 完成时推送（JSON）
     * - report: 调查结束，推送最终 EvidenceChainReport（JSON）
     * - error:  异常时推送错误信息
     */
    @PostMapping
    public SseEmitter investigate(@RequestBody Listing listing) {
        // 超时 120s
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                EvidenceChainReport report = investigationService.investigate(listing, step -> {
                    try {
                        String stepJson = mapper.writeValueAsString(step);
                        emitter.send(SseEmitter.event().name("step").data(stepJson));
                    } catch (Exception e) {
                        log.warn("推送 step 失败: {}", e.getMessage());
                    }
                });

                // 查询相似案例
                List<SimilarCase> similarCases = searchSimilarCases(listing);

                // 转换为 spec 4.5 输出结构
                Map<String, Object> specReport = buildSpecReport(report, similarCases);
                String reportJson = mapper.writeValueAsString(specReport);
                emitter.send(SseEmitter.event().name("report").data(reportJson));
                emitter.complete();
            } catch (Exception e) {
                log.error("调查异常: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignored) {
                    // emitter 可能已关闭
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * 调用 CaseVectorService 检索相似案例，异常时降级返回空列表。
     */
    private List<SimilarCase> searchSimilarCases(Listing listing) {
        try {
            String text = (listing.getTitle() != null ? listing.getTitle() : "")
                    + "\n" + (listing.getDescription() != null ? listing.getDescription() : "");
            if (text.isBlank()) {
                return List.of();
            }
            return caseVectorService.search(text, 3, listing.getPhone());
        } catch (Exception e) {
            log.warn("检索相似案例失败，降级为空: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将 EvidenceChainReport 转换为 spec 4.5 要求的输出结构。
     */
    private Map<String, Object> buildSpecReport(EvidenceChainReport report, List<SimilarCase> similarCases) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("verdict", report.getVerdict());
        root.put("trace", report.getTrace());

        Map<String, Object> reportBody = new LinkedHashMap<>();
        String summary = report.getSummary();
        if (summary != null && !summary.isBlank()) {
            reportBody.put("summary", summary);
        }

        List<Map<String, String>> evidenceList = new ArrayList<>();
        if (report.getEvidences() != null) {
            for (EvidenceChainReport.Evidence ev : report.getEvidences()) {
                Map<String, String> evMap = new LinkedHashMap<>();
                evMap.put("type", (ev.getSourceCase() != null && !ev.getSourceCase().isBlank()) ? "case" : "rule");
                evMap.put("detail", ev.getClaim());
                evidenceList.add(evMap);
            }
        }
        reportBody.put("evidence", evidenceList);

        // 构建 similarCases 输出
        List<Map<String, Object>> similarCasesList = new ArrayList<>();
        for (SimilarCase sc : similarCases) {
            Map<String, Object> scMap = new LinkedHashMap<>();
            scMap.put("listingId", sc.listingId());
            scMap.put("score", sc.score());
            scMap.put("reason", sc.reason());
            similarCasesList.add(scMap);
        }
        reportBody.put("similarCases", similarCasesList);

        root.put("report", reportBody);
        return root;
    }
}
