package io.github.shardkiht.rentdetective.llm;

import java.util.Collections;
import java.util.List;

public record ChatRequest(List<Message> messages, List<ToolSchema> tools, Double temperature) {

    public ChatRequest {
        if (tools == null) {
            tools = Collections.emptyList();
        }
        if (temperature == null) {
            temperature = 0.7;
        }
    }

    public ChatResponse.ChatResponseBuilder toResponseBuilder() {
        return ChatResponse.builder();
    }
}
