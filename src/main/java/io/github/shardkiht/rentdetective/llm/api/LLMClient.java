package io.github.shardkiht.rentdetective.llm.api;

import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.ChatResponse;

public interface LLMClient {

    ChatResponse chat(ChatRequest request);

    String engineName();
}
