package io.github.shardkiht.rentdetective.semantic.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.agent.loop.AgentContext;
import io.github.shardkiht.rentdetective.agent.tool.Tool;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.app.mapper.ListingMapper;
import io.github.shardkiht.rentdetective.rag.CaseVectorService;
import io.github.shardkiht.rentdetective.rag.SimilarCase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 价格异常检测工具（相似案例比较法）。
 * 通过向量检索找到相似房源，取可比案例价格中位数，判断当前价格偏离程度。
 * 样本不足时诚实返回"无法判断"，不硬编结论。
 */
@Component
public class CheckPriceAnomalyTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 相似度阈值：低于此值的案例不纳入比较 */
    private static final double SIMILARITY_THRESHOLD = 0.6;

    /** 最少可比案例数：不足则返回"样本不足" */
    private static final int MIN_COMPARABLE = 3;

    /** 偏离度阈值：超过 ±35% 判定异常 */
    private static final double DEVIATION_THRESHOLD = 0.35;

    private final CaseVectorService caseVectorService;
    private final ListingMapper listingMapper;

    public CheckPriceAnomalyTool(CaseVectorService caseVectorService, ListingMapper listingMapper) {
        this.caseVectorService = caseVectorService;
        this.listingMapper = listingMapper;
    }

    @Override
    public String name() {
        return "check_price_anomaly";
    }

    @Override
    public String description() {
        return "检查房源价格是否偏离相似案例的市场水平。" +
                "通过向量检索找到相似房源，比较价格中位数，判断偏离程度。" +
                "参数：price（必填，房源价格数字）、description（推荐，房源描述文本用于相似检索）、location（可选，位置描述）。";
    }

    @Override
    public String argsJsonSchema() {
        return """
                {"type":"object","properties":{"price":{"type":"number","description":"房源价格（元/月）"},"description":{"type":"string","description":"房源描述文本（用于相似案例检索）"},"location":{"type":"string","description":"位置描述（备选检索文本）"}},"required":["price"]}""";
    }

    @Override
    public ToolResult execute(String argsJson) {
        try {
            JsonNode node = MAPPER.readTree(argsJson);

            JsonNode priceNode = node.get("price");
            if (priceNode == null || !priceNode.isNumber()) {
                return ToolResult.fail("缺少必填参数 price（数字）");
            }
            double price = priceNode.asDouble();
            if (price <= 0) {
                return ToolResult.ok(MAPPER.writeValueAsString(
                        new Result("ANOMALY", price, 0, 0, 0,
                                "价格 ≤ 0，明显异常")));
            }

            String searchText = getText(node, "description", "location");
            if (searchText == null || searchText.isBlank()) {
                return ToolResult.ok(MAPPER.writeValueAsString(
                        new Result("UNKNOWN", price, 0, 0, 0,
                                "缺少描述文本，无法进行相似案例检索")));
            }

            // 从上下文获取评测集 excludeIds，避免泄题
            Set<Long> excludeIdsLong = AgentContext.getExcludeIds();
            Set<Integer> excludeIds = null;
            if (excludeIdsLong != null && !excludeIdsLong.isEmpty()) {
                excludeIds = excludeIdsLong.stream()
                        .map(Long::intValue)
                        .collect(Collectors.toSet());
            }

            // 向量检索 top-10 相似案例（排除评测集 ID）
            List<SimilarCase> cases = caseVectorService.search(searchText, 10, null, excludeIds);

            // 过滤：相似度 > 阈值 且 有有效价格
            List<Double> comparablePrices = new ArrayList<>();
            for (SimilarCase sc : cases) {
                if (sc.score() < SIMILARITY_THRESHOLD) {
                    continue;
                }
                Listing listing = listingMapper.selectById((long) sc.listingId());
                if (listing != null && listing.getPrice() != null && listing.getPrice() > 0) {
                    comparablePrices.add(listing.getPrice());
                }
            }

            // 样本不足 → 诚实返回
            if (comparablePrices.size() < MIN_COMPARABLE) {
                return ToolResult.ok(MAPPER.writeValueAsString(
                        new Result("INSUFFICIENT_DATA", price, comparablePrices.size(), 0, 0,
                                String.format("可比样本不足（仅 %d 条，需 ≥ %d），无法判断",
                                        comparablePrices.size(), MIN_COMPARABLE))));
            }

            // 计算中位数和偏离度
            double median = computeMedian(comparablePrices);
            double deviation = (price - median) / median;

            String verdict;
            String message;
            if (Math.abs(deviation) > DEVIATION_THRESHOLD) {
                verdict = "ANOMALY";
                message = String.format("价格 %.0f 元偏离相似案例中位数 %.0f 元达 %.1f%%（阈值 ±%.0f%%）",
                        price, median, deviation * 100, DEVIATION_THRESHOLD * 100);
            } else {
                verdict = "NORMAL";
                message = String.format("价格 %.0f 元处于相似案例中位数 %.0f 元的合理区间（偏离 %.1f%%）",
                        price, median, deviation * 100);
            }

            return ToolResult.ok(MAPPER.writeValueAsString(
                    new Result(verdict, price, comparablePrices.size(), median, deviation, message)));

        } catch (Exception e) {
            return ToolResult.fail("价格检测执行失败: " + e.getMessage());
        }
    }

    private double computeMedian(List<Double> prices) {
        Collections.sort(prices);
        int n = prices.size();
        if (n % 2 == 0) {
            return (prices.get(n / 2 - 1) + prices.get(n / 2)) / 2.0;
        }
        return prices.get(n / 2);
    }

    private String getText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode val = node.get(field);
            if (val != null && !val.isNull() && !val.asText().isBlank()) {
                return val.asText();
            }
        }
        return null;
    }

    private record Result(
            String verdict,
            double inputPrice,
            int comparableCount,
            double medianPrice,
            double deviation,
            String message
    ) {}
}
