package io.github.shardkiht.rentdetective.agent.tool;

import io.github.shardkiht.rentdetective.llm.ToolSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static io.github.shardkiht.rentdetective.agent.loop.AgentLoopConstants.TOOL_INVOKE_TIMEOUT_SECONDS;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tool-exec");
        t.setDaemon(true);
        return t;
    });

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
            return CompletableFuture.supplyAsync(() -> tool.execute(argsJson), executor)
                    .get(TOOL_INVOKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return ToolResult.fail("Tool '" + name + "' 执行超时（" + TOOL_INVOKE_TIMEOUT_SECONDS + "s）");
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            return ToolResult.fail(cause.getMessage());
        }
    }

}
