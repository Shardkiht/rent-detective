package io.github.shardkiht.rentdetective.agent.loop;

/**
 * Agent 调查过程中的单步轨迹记录，供 SSE 推送和最终报告展示。
 */
public record AgentStep(
        int stepIndex,
        String type,        // "thought" | "tool_call" | "tool_result" | "final_answer"
        String toolName,    // type=tool_call/tool_result 时有值，其余为 null
        String content,     // 文本内容/JSON 片段
        long timestampMs
) {
}
