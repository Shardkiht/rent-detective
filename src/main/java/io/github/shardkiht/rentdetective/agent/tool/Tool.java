package io.github.shardkiht.rentdetective.agent.tool;

public interface Tool {

    String name();

    String description();

    String argsJsonSchema();

    ToolResult execute(String argsJson);
}
