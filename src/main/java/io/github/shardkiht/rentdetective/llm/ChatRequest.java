package io.github.shardkiht.rentdetective.llm;

import java.util.Collections;
import java.util.List;

public record ChatRequest(List<Message> messages, List<ToolSchema> tools) {

    public ChatRequest {
        if (tools == null) {
            tools = Collections.emptyList();
        }
    }
}