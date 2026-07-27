package io.github.shardkiht.rentdetective.semantic.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.agent.tool.Tool;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import io.github.shardkiht.rentdetective.rag.CaseVectorService;
import io.github.shardkiht.rentdetective.rag.SimilarCase;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 相似房源检索工具。通过 RAG 向量检索，返回 Top-5 相似案例。
 */
@Component
public class SearchSimilarListingsTool implements Tool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CaseVectorService caseVectorService;

    public SearchSimilarListingsTool(CaseVectorService caseVectorService) {
        this.caseVectorService = caseVectorService;
    }

    @Override
    public String name() {
        return "search_similar_listings";
    }

    @Override
    public String description() {
        return "在已知案例库中检索与当前房源描述最相似的案例。" +
                "输入房源描述文本，返回 Top-5 相似案例（含标题、风险等级、风险标签、相似度分数）。" +
                "参数：description（必填，房源描述文本）或 text（备选字段名）。";
    }

    @Override
    public String argsJsonSchema() {
        return """
                {"type":"object","properties":{"description":{"type":"string","description":"房源描述文本"},"text":{"type":"string","description":"备选：同 description"}},"required":["description"]}""";
    }

    @Override
    public ToolResult execute(String argsJson) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(argsJson);
            String text = getTextField(node, "description", "text");
            if (text == null || text.isBlank()) {
                return ToolResult.fail("缺少参数：description 或 text");
            }

            List<SimilarCase> cases = caseVectorService.search(text, 5);
            String json = OBJECT_MAPPER.writeValueAsString(cases);
            return ToolResult.ok(json);
        } catch (JsonProcessingException e) {
            return ToolResult.fail("JSON 解析失败: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.fail("相似案例检索失败: " + e.getMessage());
        }
    }

    private String getTextField(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode val = node.get(field);
            if (val != null && !val.isNull() && !val.asText().isBlank()) {
                return val.asText();
            }
        }
        return null;
    }
}
