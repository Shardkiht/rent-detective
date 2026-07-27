package io.github.shardkiht.rentdetective.llm.impl;

import io.github.shardkiht.rentdetective.llm.api.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class FallbackEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackEmbeddingClient.class);

    private final EmbeddingClient primary;
    private final EmbeddingClient secondary;

    public FallbackEmbeddingClient(OpenAiCompatibleEmbeddingClient primary, OllamaEmbeddingClient secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public float[] embed(String text) {
        try {
            return primary.embed(text);
        } catch (Exception primaryEx) {
            log.warn("云端 embedding 失败，降级到本地: {}", primaryEx.getMessage());
            return secondary.embed(text);
        }
    }
}
