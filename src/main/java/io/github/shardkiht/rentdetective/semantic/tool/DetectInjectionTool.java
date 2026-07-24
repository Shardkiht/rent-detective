package io.github.shardkiht.rentdetective.semantic.tool;

import io.github.shardkiht.rentdetective.agent.tool.Tool;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

@Component
public class DetectInjectionTool implements Tool {

    @Override
    public String name() {
        return "detect_injection";
    }

    @Override
    public String description() {
        return "Prompt injection 检测";
    }

    @Override
    public String argsJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\"}},\"required\":[\"input\"]}";
    }

    @Override
    public ToolResult execute(String argsJson) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
