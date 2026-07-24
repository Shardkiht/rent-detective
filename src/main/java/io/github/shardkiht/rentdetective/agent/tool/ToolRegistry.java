package io.github.shardkiht.rentdetective.agent.tool;

import io.github.shardkiht.rentdetective.llm.ToolSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        toolList.forEach(this::register);
    }

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public List<ToolSchema> schemas() {
        return tools.values().stream()
                .map(t -> new ToolSchema(t.name(), t.description(), t.argsJsonSchema()))
                .toList();
    }

    public ToolResult invoke(String name, String argsJson) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.fail("Tool not found: " + name);
        }
        try {
            return tool.execute(argsJson);
        } catch (Exception e) {
            return ToolResult.fail(e.getMessage());
        }
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }
}
