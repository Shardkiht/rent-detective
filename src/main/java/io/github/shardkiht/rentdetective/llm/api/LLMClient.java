package io.github.shardkiht.rentdetective.llm.api;

import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.ChatResponse;

public interface LLMClient {

    ChatResponse chat(ChatRequest request);

    default void chatStream(ChatRequest request, StreamCallback callback) {
        throw new UnsupportedOperationException("Streaming not supported");
    }

    String engineName();
}
