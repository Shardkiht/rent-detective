package io.github.shardkiht.rentdetective.semantic.tool;

import io.github.shardkiht.rentdetective.agent.tool.Tool;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

@Component
public class SearchSimilarListingsTool implements Tool {

    @Override
    public String name() {
        return "search_similar_listings";
    }

    @Override
    public String description() {
        return "二房东关联检测";
    }

    @Override
    public String argsJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"phone\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"}},\"required\":[\"phone\"]}";
    }

    @Override
    public ToolResult execute(String argsJson) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
