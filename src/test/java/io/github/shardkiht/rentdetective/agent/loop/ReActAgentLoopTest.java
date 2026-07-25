package io.github.shardkiht.rentdetective.agent.loop;

import io.github.shardkiht.rentdetective.agent.report.EvidenceChainReport;
import io.github.shardkiht.rentdetective.agent.tool.ToolRegistry;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.llm.ChatResponse;
import io.github.shardkiht.rentdetective.llm.api.LLMClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ReActAgentLoop 单元测试。
 * mock LLMClient 和 ToolRegistry，不依赖真实模型调用。
 */
class ReActAgentLoopTest {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentLoopTest.class);

    private LLMClient llmClient;
    private ToolRegistry toolRegistry;
    private ReActAgentLoop agentLoop;

    private static final String VALID_VERDICT_JSON = """
            {"verdict": "SCAM", "confidence": 0.85, "evidences": [{"claim": "价格远低于市场价", "sourceTool": "check_price_anomaly", "sourceCase": null, "quote": "月租1500元，同区域均价4500元"}]}""";

    @BeforeEach
    void setUp() {
        llmClient = mock(LLMClient.class);
        toolRegistry = mock(ToolRegistry.class);
        agentLoop = new ReActAgentLoop(llmClient, toolRegistry);

        when(toolRegistry.schemas()).thenReturn(Collections.emptyList());
    }

    private Listing sampleListing() {
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTitle("朝阳区精装两居 急租");
        listing.setPrice(1500.0);
        listing.setLocation("北京朝阳区");
        listing.setSource("58同城");
        listing.setUrl("https://example.com/1");
        listing.setDescription("精装修，拎包入住，月租1500，先到先得。");
        return listing;
    }

    /**
     * 模型第一轮直接返回合法结论 JSON（无工具调用）→ 一步收敛，converged=true
     */
    @Test
    void directVerdictConvergesInOneStep() {
        when(llmClient.chat(any())).thenReturn(
                ChatResponse.builder().content(VALID_VERDICT_JSON).hasToolCall(false).degraded(false).build());

        EvidenceChainReport result = agentLoop.investigate(sampleListing());

        assertThat(result.isConverged()).isTrue();
        assertThat(result.getVerdict()).isEqualTo("SCAM");
        assertThat(result.getConfidence()).isEqualTo(0.85);
        assertThat(result.getEvidences()).hasSize(1);
        assertThat(result.getTrace()).isNotEmpty();
        // 只调用了一次模型
        verify(llmClient, times(1)).chat(any());
        log.info("[PASS] directVerdictConvergesInOneStep: verdict={}, confidence={}, converged={}",
                result.getVerdict(), result.getConfidence(), result.isConverged());
    }

    /**
     * 模型先请求一次工具调用，工具执行成功，第二轮返回合法结论
     * → trace 里正确记录 tool_call 和 tool_result
     */
    @Test
    void toolCallThenVerdictRecordsTrace() {
        // 第一轮：模型请求调用工具
        ChatResponse toolCallResponse = ChatResponse.builder()
                .content("")
                .hasToolCall(true)
                .toolCall(new ChatResponse.ToolCall("check_price_anomaly", "{\"listingId\": 1}"))
                .degraded(false)
                .build();

        // 第二轮：模型返回结论
        ChatResponse verdictResponse = ChatResponse.builder()
                .content(VALID_VERDICT_JSON)
                .hasToolCall(false)
                .degraded(false)
                .build();

        when(llmClient.chat(any())).thenReturn(toolCallResponse, verdictResponse);
        when(toolRegistry.invoke("check_price_anomaly", "{\"listingId\": 1}"))
                .thenReturn(ToolResult.ok("{\"anomalyScore\": 0.9}"));

        EvidenceChainReport result = agentLoop.investigate(sampleListing());

        assertThat(result.isConverged()).isTrue();
        assertThat(result.getVerdict()).isEqualTo("SCAM");

        // 验证 trace 包含 tool_call 和 tool_result
        List<String> traceTypes = result.getTrace().stream().map(AgentStep::type).toList();
        assertThat(traceTypes).contains("tool_call", "tool_result", "final_answer");

        List<String> toolNames = result.getTrace().stream()
                .map(AgentStep::toolName)
                .filter(Objects::nonNull)
                .toList();
        assertThat(toolNames).contains("check_price_anomaly");
        log.info("[PASS] toolCallThenVerdictRecordsTrace: traceTypes={}, toolNames={}", traceTypes, toolNames);
    }

    /**
     * 模型第一轮返回非法内容 → 追加提醒重试，第二轮返回合法 JSON 后正常收敛
     */
    @Test
    void invalidFormatRetriesAndConverges() {
        ChatResponse invalidResponse = ChatResponse.builder()
                .content("我觉得这个房源可能有问题，但我不确定。")
                .hasToolCall(false)
                .degraded(false)
                .build();

        ChatResponse validResponse = ChatResponse.builder()
                .content(VALID_VERDICT_JSON)
                .hasToolCall(false)
                .degraded(false)
                .build();

        when(llmClient.chat(any())).thenReturn(invalidResponse, validResponse);

        EvidenceChainReport result = agentLoop.investigate(sampleListing());

        assertThat(result.isConverged()).isTrue();
        assertThat(result.getVerdict()).isEqualTo("SCAM");
        // 调用了两次模型
        verify(llmClient, times(2)).chat(any());
        log.info("[PASS] invalidFormatRetriesAndConverges: verdict={}, converged={}", result.getVerdict(), result.isConverged());
    }

    /**
     * 连续 MAX_STEPS 轮都不给出合法结论 → 触发 forceConclude，
     * converged=false，confidence <= 0.5
     */
    @Test
    void maxStepsTriggersForceConcludeWithCappedConfidence() {
        // 所有调用都返回非法内容（非 JSON）
        ChatResponse invalidResponse = ChatResponse.builder()
                .content("我还在分析中...")
                .hasToolCall(false)
                .degraded(false)
                .build();

        // forceConclude 时最后一次调用返回一个高置信度 JSON（验证封顶逻辑）
        ChatResponse forceResponse = ChatResponse.builder()
                .content("{\"verdict\": \"SUSPECT\", \"confidence\": 0.9, \"evidences\": []}")
                .hasToolCall(false)
                .degraded(false)
                .build();

        // 前 8 次返回非法内容，第 9 次（forceConclude）返回合法 JSON
        when(llmClient.chat(any()))
                .thenReturn(invalidResponse, invalidResponse, invalidResponse, invalidResponse,
                        invalidResponse, invalidResponse, invalidResponse, invalidResponse,
                        forceResponse);

        EvidenceChainReport result = agentLoop.investigate(sampleListing());

        assertThat(result.isConverged()).isFalse();
        // confidence 被封顶到 0.5，即使模型输出了 0.9
        assertThat(result.getConfidence()).isEqualTo(0.5);
        assertThat(result.getVerdict()).isEqualTo("SUSPECT");
        log.info("[PASS] maxStepsTriggersForceConcludeWithCappedConfidence: verdict={}, confidence={} (capped from 0.9), converged={}",
                result.getVerdict(), result.getConfidence(), false);
    }

    /**
     * 工具调用 ToolResult.success()==false → 不抛异常，error 信息喂回对话继续
     */
    @Test
    void toolFailureDoesNotAbortInvestigation() {
        // 第一轮：模型请求调用工具
        ChatResponse toolCallResponse = ChatResponse.builder()
                .content("")
                .hasToolCall(true)
                .toolCall(new ChatResponse.ToolCall("search_similar", "{\"query\": \"test\"}"))
                .degraded(false)
                .build();

        // 第二轮：模型返回结论
        ChatResponse verdictResponse = ChatResponse.builder()
                .content(VALID_VERDICT_JSON)
                .hasToolCall(false)
                .degraded(false)
                .build();

        when(llmClient.chat(any())).thenReturn(toolCallResponse, verdictResponse);
        when(toolRegistry.invoke("search_similar", "{\"query\": \"test\"}"))
                .thenReturn(ToolResult.fail("网络超时"));

        // 不应抛异常
        EvidenceChainReport result = agentLoop.investigate(sampleListing());

        assertThat(result.isConverged()).isTrue();
        assertThat(result.getVerdict()).isEqualTo("SCAM");

        // trace 中 tool_result 记录了错误信息
        AgentStep toolResultStep = result.getTrace().stream()
                .filter(s -> "tool_result".equals(s.type()))
                .findFirst()
                .orElseThrow();
        assertThat(toolResultStep.content()).contains("ERROR");
        log.info("[PASS] toolFailureDoesNotAbortInvestigation: verdict={}, toolError fed back, converged={}",
                result.getVerdict(), true);
    }
}
