package io.github.shardkiht.rentdetective.llm;

import lombok.Builder;

import java.util.Collections;
import java.util.Map;

@Builder
public record ChatResponse(String content,
                           boolean hasToolCall,
                           ToolCall toolCall,
                           boolean degraded,
                           Map<String, Object> metadata) {

    public ChatResponse {
        if (metadata == null) {
            metadata = Collections.emptyMap();
        }
    }

    public record ToolCall(String name, String argsJson) {
    }
}
