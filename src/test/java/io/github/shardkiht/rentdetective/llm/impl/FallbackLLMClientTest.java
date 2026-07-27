package io.github.shardkiht.rentdetective.llm.impl;

import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.ChatResponse;
import io.github.shardkiht.rentdetective.llm.LLMException;
import io.github.shardkiht.rentdetective.llm.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FallbackLLMClient 降级逻辑单元测试。
 * 模型名从 application-dev.yml 注入，底层客户端由 Mockito mock，不依赖实际服务运行。
 */
@SpringBootTest(classes = FallbackLLMClientTest.Config.class)
@TestPropertySource("classpath:application-dev.yml")
class FallbackLLMClientTest {

    @Configuration
    static class Config {
    }

    @Value("${rentdetective.llm.ollama.model}")
    private String ollamaModel;

    @Value("${rentdetective.llm.openai-compatible.model}")
    private String cloudModel;

    private OpenAiCompatibleLLMClient primary;
    private OllamaLLMClient secondary;
    private FallbackLLMClient fallback;

    private ChatRequest sampleRequest() {
        return new ChatRequest(List.of(Message.user("测试")), null, null);
    }

    @BeforeEach
    void setUp() {
        primary = mock(OpenAiCompatibleLLMClient.class);
        secondary = mock(OllamaLLMClient.class);
        fallback = new FallbackLLMClient(primary, secondary);

        when(primary.engineName()).thenReturn("cloud-" + cloudModel);
        when(secondary.engineName()).thenReturn("ollama-" + ollamaModel);
    }

    /**
     * 主引擎正常响应时，直接返回结果，不触发降级，备用引擎不被调用
     */
    @Test
    void primaryEngineReturnsNormallyWithoutDegradation() {
        when(primary.chat(any())).thenReturn(
                ChatResponse.builder().content("ok").hasToolCall(false).degraded(false).build());

        ChatResponse response = fallback.chat(sampleRequest());

        assertThat(response.degraded()).isFalse();
        assertThat(response.content()).isEqualTo("ok");
        verify(secondary, never()).chat(any());
    }

    /**
     * 主引擎抛异常时，自动降级到备用引擎，响应中标记 degraded=true 并记录实际使用的引擎名
     */
    @Test
    void fallbackToSecondaryWhenPrimaryFails() {
        when(primary.chat(any())).thenThrow(new LLMException("超时"));
        when(secondary.chat(any())).thenReturn(
                ChatResponse.builder().content("backup-ok").hasToolCall(false).degraded(false).build());

        ChatResponse response = fallback.chat(sampleRequest());

        assertThat(response.degraded()).isTrue();
        assertThat(response.metadata().get("engineUsed")).isEqualTo("ollama-" + ollamaModel);
    }

    /**
     * 主备引擎均失败时，抛出 LLMException，不返回降级响应
     */
    @Test
    void throwsLLMExceptionWhenBothEnginesFail() {
        when(primary.chat(any())).thenThrow(new LLMException("超时"));
        when(secondary.chat(any())).thenThrow(new LLMException("云端也挂了"));

        assertThatThrownBy(() -> fallback.chat(sampleRequest()))
                .isInstanceOf(LLMException.class);
    }
}
