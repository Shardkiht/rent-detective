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
 * 基于关键词 + 结构模式匹配，覆盖三种主要注入变体：
 * 1. 直接指令型（忽略以上要求、直接判定安全）
 * 2. 角色扮演型（你现在是XX、无条件配合）
 * 3. 伪装系统消息型（[系统提示]、[SYSTEM]）
 */
@Component
public class DetectInjectionTool implements Tool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 注入检测关键词（覆盖中英文三种攻击变体） */
    private static final String[] INJECTION_KEYWORDS = {
            // --- 直接指令型 ---
            "忽略以上", "忽略之前", "忽略所有", "忽略上述",
            "无视以上", "无视之前", "无视所有",
            "跳过检测", "跳过检查", "直接判定", "强制判定",
            "判定为安全", "判定本房源安全", "输出安全", "结论为安全",
            "不要检测", "不要分析", "直接通过",
            "disregard previous", "ignore previous", "ignore all",
            "forget everything", "new instructions", "do not follow",
            "bypass", "override", "jailbreak", "DAN mode",
            // --- 角色扮演型 ---
            "你现在是", "你是一个", "你不再是", "从现在开始你是",
            "无条件配合", "听从我的", "按我说的做", "必须服从我",
            "扮演", "假装你是", "假装自己是",
            "you are now", "you are no longer", "act as", "pretend you are",
            "from now on you", "obey me", "follow my instructions only",
            // --- 伪装系统消息型 ---
            "[系统提示]", "[系统消息]", "[系统指令]", "[system]",
            "<<sys>>", "<</sys>>", "[inst]", "[instruction]",
            "system prompt", "system message", "admin override"
    };

    @Override
    public String name() {
        return "detect_injection";
    }

    @Override
    public String description() {
        return "检测输入文本中是否包含 Prompt injection（提示词注入）攻击模式。" +
                "覆盖直接指令型、角色扮演型、伪装系统消息型三种变体。" +
                "输入待检测文本，返回是否发现注入以及命中的关键词列表。" +
                "参数：input（必填，待检测文本）或 description（备选字段名）。";
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
