package io.github.shardkiht.rentdetective.agent.tool;

public record ToolResult(boolean success, String dataJson, String error) {

    public static ToolResult ok(String dataJson) {
        return new ToolResult(true, dataJson, null);
    }

    public static ToolResult fail(String error) {
        return new ToolResult(false, null, error);
    }
}
