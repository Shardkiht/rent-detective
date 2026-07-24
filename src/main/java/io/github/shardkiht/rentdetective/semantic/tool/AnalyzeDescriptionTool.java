package io.github.shardkiht.rentdetective.semantic.tool;

import io.github.shardkiht.rentdetective.agent.tool.Tool;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

@Component
public class AnalyzeDescriptionTool implements Tool {

    @Override
    public String name() {
        return "analyze_description";
    }

    @Override
    public String description() {
        return "话术套路检测";
    }

    @Override
    public String argsJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"description\":{\"type\":\"string\"}},\"required\":[\"description\"]}";
    }

    @Override
    public ToolResult execute(String argsJson) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
