package io.github.shardkiht.rentdetective.agent.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.agent.report.EvidenceChainReport;
import io.github.shardkiht.rentdetective.agent.tool.ToolRegistry;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.ChatResponse;
import io.github.shardkiht.rentdetective.llm.Message;
import io.github.shardkiht.rentdetective.llm.ToolSchema;
import io.github.shardkiht.rentdetective.llm.api.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static io.github.shardkiht.rentdetective.agent.loop.AgentLoopConstants.*;

@Component
public class ReActAgentLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentLoop.class);

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper;



    public ReActAgentLoop(LLMClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.mapper = new ObjectMapper();
    }

    public EvidenceChainReport investigate(Listing listing) {
        return investigate(listing, step -> {});
    }

    public EvidenceChainReport investigate(Listing listing, Consumer<AgentStep> onStep) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(SYSTEM_PROMPT));
        messages.add(Message.user(formatListingInfo(listing)));

        List<AgentStep> trace = new ArrayList<>();
        List<ToolSchema> toolSchemas = toolRegistry.schemas();

        for (int step = 1; step <= MAX_STEPS; step++) {
            ChatResponse response = callModel(messages, toolSchemas);

            if (response.hasToolCall()) {
                // 记录 tool_call 轨迹
                emitStep(onStep, trace, new AgentStep(step, "tool_call",
                        response.toolCall().name(), response.toolCall().argsJson(), now()));

                ToolResult toolResult = toolRegistry.invoke(
                        response.toolCall().name(), response.toolCall().argsJson());

                String resultContent = toolResult.success()
                        ? toolResult.dataJson()
                        : "ERROR: " + toolResult.error();

                // 记录 tool_result 轨迹
                emitStep(onStep, trace, new AgentStep(step, "tool_result",
                        response.toolCall().name(), resultContent, now()));

                // 追加 assistant 的 tool_call 请求 + tool 返回结果到对话历史
                messages.add(Message.assistant(response.content()));
                messages.add(Message.tool(response.toolCall().name(),
                        toolResult.success() ? toolResult.dataJson() : ("调用失败: " + toolResult.error())));

            } else {
                // 没有工具调用，模型认为可以给结论了
                emitStep(onStep, trace, new AgentStep(step, "thought", null, response.content(), now()));

                EvidenceChainReport parsed = tryParseVerdict(response.content());
                if (parsed != null) {
                    emitStep(onStep, trace, new AgentStep(step, "final_answer", null, response.content(), now()));
                    return buildResult(parsed, trace, true);
                } else {
                    // 格式不对，追加提醒让模型重新按格式回答
                    messages.add(Message.assistant(response.content()));
                    messages.add(Message.user(
                            "你的回答不是合法的 JSON 结论格式，请严格按照要求的格式重新输出，不要包含其他文字。"));
                }
            }
        }

        // 达到步数上限仍未收敛，强制结束
        return forceConclude(messages, trace, onStep);
    }

    // ==================== 内部辅助方法 ====================

    private ChatResponse callModel(List<Message> messages, List<ToolSchema> toolSchemas) {
        return llmClient.chat(new ChatRequest(messages, toolSchemas, 0.3));
    }

    /**
     * 达到 MAX_STEPS 上限后的强制收尾逻辑。
     * 关键：强制收敛的结论 confidence 封顶 0.5，防止 Agent 卡步数上限时输出虚高置信度。
     */
    private EvidenceChainReport forceConclude(List<Message> messages, List<AgentStep> trace,
                                              Consumer<AgentStep> onStep) {
        messages.add(Message.user(
                "已达到最大调查步数，请基于目前已获得的信息直接给出结论 JSON，不要再请求调用任何工具。"));

        // 不允许再调用工具，传空 toolSchemas
        ChatResponse response = callModel(messages, Collections.emptyList());

        emitStep(onStep, trace, new AgentStep(MAX_STEPS, "final_answer", null, response.content(), now()));

        EvidenceChainReport parsed = tryParseVerdict(response.content());
        if (parsed == null) {
            // 连强制收尾都解析失败，兜底给一个最低置信度的 SUSPECT 结论
            parsed = new EvidenceChainReport();
            parsed.setVerdict("SUSPECT");
            parsed.setConfidence(0.3);
            EvidenceChainReport.Evidence fallbackEvidence = new EvidenceChainReport.Evidence();
            fallbackEvidence.setClaim("Agent 未能在规定步数内给出结构化结论，标记为疑似待人工复核");
            parsed.setEvidences(List.of(fallbackEvidence));
        } else {
            // 强制收敛的结论 confidence 封顶 0.5
            parsed.setConfidence(Math.min(parsed.getConfidence(), 0.5));
        }

        return buildResult(parsed, trace, false);
    }

    /**
     * 防御式解析：优先尝试直接解析；失败则提取文本中第一个 { ... } JSON 块再解析。
     * 任何解析异常都返回 null，不打断主循环。
     */
    private EvidenceChainReport tryParseVerdict(String content) {
        try {
            String json = extractJsonObject(content);
            if (json == null) {
                return null;
            }

            JsonNode node = mapper.readTree(json);

            JsonNode verdictNode = node.get("verdict");
            if (verdictNode == null || !VALID_VERDICTS.contains(verdictNode.asText())) {
                return null;
            }

            JsonNode confidenceNode = node.get("confidence");
            if (confidenceNode == null) {
                return null;
            }
            double confidence = confidenceNode.asDouble();
            if (confidence < 0 || confidence > 1) {
                return null;
            }

            JsonNode evidencesNode = node.get("evidences");
            if (evidencesNode == null || !evidencesNode.isArray()) {
                return null;
            }

            List<EvidenceChainReport.Evidence> evidences = new ArrayList<>();
            for (JsonNode ev : evidencesNode) {
                EvidenceChainReport.Evidence evidence = new EvidenceChainReport.Evidence();
                evidence.setClaim(ev.path("claim").asText(null));
                evidence.setSourceTool(ev.path("sourceTool").isNull() ? null : ev.path("sourceTool").asText(null));
                evidence.setSourceCase(ev.path("sourceCase").isNull() ? null : ev.path("sourceCase").asText(null));
                evidence.setQuote(ev.path("quote").isNull() ? null : ev.path("quote").asText(null));
                evidences.add(evidence);
            }

            EvidenceChainReport report = new EvidenceChainReport();
            report.setVerdict(verdictNode.asText());
            report.setConfidence(confidence);
            report.setEvidences(evidences);
            return report;
        } catch (Exception e) {
            log.debug("解析结论 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 括号计数算法：找到第一个 { 及与之匹配的 }，避免贪婪正则把无关花括号框进来。
     */
    private String extractJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return trimmed.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private EvidenceChainReport buildResult(EvidenceChainReport report, List<AgentStep> trace, boolean converged) {
        report.setConverged(converged);
        report.setTrace(trace);
        return report;
    }

    private void emitStep(Consumer<AgentStep> onStep, List<AgentStep> trace, AgentStep step) {
        trace.add(step);
        onStep.accept(step);
    }

    private String formatListingInfo(Listing listing) {
        StringBuilder sb = new StringBuilder("请调查以下房源信息：\n");
        sb.append("标题: ").append(listing.getTitle()).append("\n");
        sb.append("价格: ").append(listing.getPrice()).append(" 元/月\n");
        sb.append("位置: ").append(listing.getLocation()).append("\n");
        sb.append("来源: ").append(listing.getSource()).append("\n");
        sb.append("链接: ").append(listing.getUrl()).append("\n");
        sb.append("描述: ").append(listing.getDescription()).append("\n");
        if (listing.getPhone() != null) {
            sb.append("联系电话: ").append(listing.getPhone()).append("\n");
        }
        return sb.toString();
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
