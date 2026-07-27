package io.github.shardkiht.rentdetective.llm.impl;

import io.github.shardkiht.rentdetective.llm.*;
import io.github.shardkiht.rentdetective.llm.api.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Primary
@Component
public class FallbackLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackLLMClient.class);

    private final LLMClient primary;
    private final LLMClient secondary;

    public FallbackLLMClient(OpenAiCompatibleLLMClient primary, OllamaLLMClient secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    // TODO: 临时禁用本地降级，避免 GPU 满载风扇狂转。恢复时删除此标志并还原 catch 块
    private static final boolean FALLBACK_DISABLED = true;

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            return withEngineMeta(primary.chat(request), primary.engineName(), false);
        } catch (Exception primaryEx) {
            if (FALLBACK_DISABLED) {
                log.warn("主引擎 {} 调用失败，本地降级已禁用，直接抛出: {}", primary.engineName(), primaryEx.getMessage());
                throw new LLMException("主引擎调用失败(降级已禁用): " + primaryEx.getMessage(), primaryEx);
            }
            log.warn("主引擎 {} 调用失败，降级到备用引擎: {}", primary.engineName(), primaryEx.getMessage());
            try {
                return withEngineMeta(secondary.chat(request), secondary.engineName(), true);
            } catch (Exception secondaryEx) {
                log.error("备用引擎 {} 也调用失败", secondary.engineName(), secondaryEx);
                throw new LLMException("主备引擎均调用失败: primary=" + primaryEx.getMessage()
                        + ", secondary=" + secondaryEx.getMessage(), secondaryEx);
            }
        }
    }

    private ChatResponse withEngineMeta(ChatResponse response, String engineUsed, boolean degraded) {
        Map<String, Object> metadata = new HashMap<>(response.metadata());
        metadata.put("engineUsed", engineUsed);
        return new ChatResponse(response.content(), response.hasToolCall(), response.toolCall(), degraded, metadata);
    }

    @Override
    public String engineName() {
        return "fallback[" + primary.engineName() + "->" + secondary.engineName() + "]";
    }
}