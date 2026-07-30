package io.github.shardkiht.rentdetective.app.service;

import io.github.shardkiht.rentdetective.agent.loop.AgentStep;
import io.github.shardkiht.rentdetective.agent.loop.ReActAgentLoop;
import io.github.shardkiht.rentdetective.agent.report.EvidenceChainReport;
import io.github.shardkiht.rentdetective.domain.entity.Listing;
import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.Message;
import io.github.shardkiht.rentdetective.llm.api.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 调查服务：调用 ReActAgentLoop 执行房源风险调查。
 * Agent 循环手写，工具可插拔；识坑规则来自人工标注，在 rules 包。
 */
@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final ReActAgentLoop agentLoop;
    private final LLMClient llmClient;

    public InvestigationService(ReActAgentLoop agentLoop, LLMClient llmClient) {
        this.agentLoop = agentLoop;
        this.llmClient = llmClient;
    }

    /**
     * 直接对给定 Listing 执行调查，支持 step 回调（用于 SSE 推送）。
     */
    public EvidenceChainReport investigate(Listing listing, Consumer<AgentStep> onStep) {
        EvidenceChainReport report = agentLoop.investigate(listing, onStep);

        // 生成 summary：对 final_answer 的一句话概括
        String finalAnswer = extractFinalAnswer(report.getTrace());
        if (finalAnswer != null && !finalAnswer.isBlank()) {
            String summary = null;
            try {
                var response = llmClient.chat(new ChatRequest(
                        List.of(
                                Message.system("把下面的调查结论压缩成一句话（不超过50字），只说结论和最关键的一个理由，不要复述细节。"),
                                Message.user(finalAnswer)
                        ),
                        null,
                        0.3
                ));
                if (response != null && response.content() != null && !response.content().isBlank()) {
                    summary = response.content();
                }
            } catch (Exception e) {
                log.warn("summary 生成失败，降级为原文: {}", e.getMessage());
            }
            // 兜底：失败就用原文，绝不留空
            report.setSummary(summary != null ? summary : finalAnswer);
        }

        return report;
    }

    /**
     * 从调查轨迹末尾提取 Agent 最终结论（final_answer）。
     * 倒序查找：若循环经多次 final（如解析失败自我修正后再收敛），取收敛那次的结论。
     */
    private String extractFinalAnswer(List<AgentStep> trace) {
        if (trace == null) {
            return null;
        }
        for (int i = trace.size() - 1; i >= 0; i--) {
            AgentStep step = trace.get(i);
            if (step != null
                    && "final_answer".equals(step.type())
                    && step.content() != null
                    && !step.content().isBlank()) {
                return step.content();
            }
        }
        return null;
    }
}
