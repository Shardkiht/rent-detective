package io.github.shardkiht.rentdetective.llm.impl;

import io.github.shardkiht.rentdetective.llm.EmbeddingClient;
import org.springframework.stereotype.Component;

@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    @Override
    public float[] embed(String text) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
