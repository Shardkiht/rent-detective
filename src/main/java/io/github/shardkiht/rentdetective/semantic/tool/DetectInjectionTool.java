package io.github.shardkiht.rentdetective.semantic.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.agent.tool.Tool;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt injection 检测工具。
 * TODO: 规则词表由作者提供后实现——当前为占位检测，仅覆盖基本注入模式。
 */
@Component
public class DetectInjectionTool implements Tool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 基本注入检测关键词（占位实现）。
     * TODO: 规则词表由作者提供后替换为完整的注入模式词库。
     */
    private static final String[] INJECTION_KEYWORDS = {
            "忽略以上指令",
            "忽略之前",
            "忽略所有",
            " disregard previous",
            "ignore previous",
            "ignore all",
            "system prompt",
            "你现在是",
            "你是一个",
            "you are now",
            "act as",
            "pretend you are",
            "override",
            "jailbreak",
            "DAN mode"
    };

    @Override
    public String name() {
        return "detect_injection";
    }

    @Override
    public String description() {
        return "检测输入文本中是否包含 Prompt injection（提示词注入）攻击模式。" +
                "输入待检测文本，返回是否发现注入以及命中的关键词列表。" +
                "参数：input（必填，待检测文本）或 description（备选字段名）。" +
                "注意：当前为占位实现，规则词表由作者提供后完善。";
    }

    @Override
    public String argsJsonSchema() {
        return """
                {"type":"object","properties":{"input":{"type":"string","description":"待检测文本"},"description":{"type":"string","description":"备选：同 input"}},"required":["input"]}""";
    }

    @Override
    public ToolResult execute(String argsJson) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(argsJson);
            String input = getTextField(node, "input", "description");
            if (input == null || input.isBlank()) {
                return ToolResult.fail("缺少参数：input 或 description");
            }

            String lowerInput = input.toLowerCase();
            List<String> matched = new ArrayList<>();
            for (String keyword : INJECTION_KEYWORDS) {
                if (lowerInput.contains(keyword.toLowerCase())) {
                    matched.add(keyword);
                }
            }

            boolean detected = !matched.isEmpty();
            String json = OBJECT_MAPPER.writeValueAsString(new InjectionResult(
                    detected,
                    matched,
                    detected ? "检测到 " + matched.size() + " 个注入关键词" : "未检测到注入模式"
            ));
            return ToolResult.ok(json);
        } catch (JsonProcessingException e) {
            return ToolResult.fail("JSON 解析失败: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.fail("注入检测失败: " + e.getMessage());
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

    /** 注入检测结果 JSON 结构 */
    private record InjectionResult(boolean detected, List<String> matchedKeywords, String summary) {}
}
