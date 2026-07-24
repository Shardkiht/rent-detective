package io.github.shardkiht.rentdetective.llm.impl;

import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.ChatResponse;
import io.github.shardkiht.rentdetective.llm.LLMClient;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleLLMClient implements LLMClient {

    @Override
    public ChatResponse chat(ChatRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String engineName() {
        return "openai-compatible";
    }
}
