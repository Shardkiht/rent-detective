package io.github.shardkiht.rentdetective.semantic.tool;

import io.github.shardkiht.rentdetective.agent.tool.Tool;
import io.github.shardkiht.rentdetective.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

@Component
public class CheckPriceAnomalyTool implements Tool {

    @Override
    public String name() {
        return "check_price_anomaly";
    }

    @Override
    public String description() {
        return "价格偏离度比对";
    }

    @Override
    public String argsJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"price\":{\"type\":\"number\"},\"location\":{\"type\":\"string\"}},\"required\":[\"price\",\"location\"]}";
    }

    @Override
    public ToolResult execute(String argsJson) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
