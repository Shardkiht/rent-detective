package io.github.shardkiht.rentdetective.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.domain.entity.Listing;
import io.github.shardkiht.rentdetective.rules.engine.EngineResult;
import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleEngine;
import io.github.shardkiht.rentdetective.rules.pricing.PriceExtractor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 话术套路检测工具。基于规则引擎对房源描述进行识坑分析。
 * 规则来自 104 条人工标注。
 */
@Component
public class AnalyzeDescriptionTool implements Tool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RuleEngine ruleEngine;
    private final PriceExtractor priceExtractor;

    public AnalyzeDescriptionTool(RuleEngine ruleEngine, PriceExtractor priceExtractor) {
        this.ruleEngine = ruleEngine;
        this.priceExtractor = priceExtractor;
    }

    @Override
    public String name() {
        return "analyze_description";
    }

    @Override
    public String description() {
        return "对房源描述进行话术套路检测，基于 104 条人工标注规则进行识坑分析。" +
                "输入房源描述文本（必填），可选传入标题和价格。" +
                "返回判定结果（SAFE/SUSPICIOUS/REVIEW/INSUFFICIENT/NOT_LISTING）、风险分数、命中规则、建议。" +
                "参数：description（必填，房源描述全文）、title（可选，房源标题）、price（可选，标注价格数字）。";
    }

    @Override
    public String argsJsonSchema() {
        return """
                {"type":"object","properties":{"description":{"type":"string","description":"房源描述全文"},"title":{"type":"string","description":"房源标题（可选）"},"price":{"type":"number","description":"标注价格（可选）"}},"required":["description"]}""";
    }

    @Override
    public ToolResult execute(String argsJson) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(argsJson);

            String description = getTextOrNull(node, "description");
            if (description == null || description.isBlank()) {
                return ToolResult.fail("缺少参数：description");
            }

            String title = getTextOrNull(node, "title");
            Double price = null;
            JsonNode priceNode = node.get("price");
            if (priceNode != null && !priceNode.isNull()) {
                price = priceNode.asDouble();
            }

            // 构造最小 Listing 对象，用于复用 ListingContext.fromListing 的解析逻辑
            Listing listing = new Listing();
            listing.setTitle(title != null ? title : "");
            listing.setDescription(description);
            listing.setPrice(price);

            ListingContext ctx = ListingContext.fromListing(listing, priceExtractor);
            EngineResult result = ruleEngine.evaluate(ctx);

            // 序列化为 JSON
            String json = OBJECT_MAPPER.writeValueAsString(new AnalyzeResult(
                    result.verdict().name(),
                    result.score(),
                    result.hits().stream().map(h -> new HitRecord(h.ruleType(), h.weight(), h.evidence())).toList(),
                    result.advice(),
                    result.reason()
            ));
            return ToolResult.ok(json);
        } catch (JsonProcessingException e) {
            return ToolResult.fail("JSON 解析失败: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.fail("话术分析失败: " + e.getMessage());
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull()) return null;
        return val.asText();
    }

    /** 工具返回的 JSON 结构 */
    private record AnalyzeResult(String verdict, double score, List<HitRecord> hits,
                                  List<String> advice, String reason) {}

    private record HitRecord(String ruleType, double weight, String evidence) {}
}
