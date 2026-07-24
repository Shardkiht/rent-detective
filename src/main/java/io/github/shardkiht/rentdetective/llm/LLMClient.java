package io.github.shardkiht.rentdetective.llm;

public interface LLMClient {

    ChatResponse chat(ChatRequest request);

    default void chatStream(ChatRequest request, StreamCallback callback) {
        throw new UnsupportedOperationException("Streaming not supported");
    }

    String engineName();
}
