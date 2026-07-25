package io.github.shardkiht.rentdetective.llm.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.Message;
import io.github.shardkiht.rentdetective.llm.ToolSchema;

/**
 * llm.impl 包内共享的请求体构建工具。
 */
final class LlmRequestUtils {

    private LlmRequestUtils() {
    }

    /**
     * 将 ChatRequest 中的 messages 写入 JSON body 的 "messages" 数组。
     */
    static void appendMessages(ObjectNode body, ChatRequest request) {
        ArrayNode messages = body.putArray("messages");
        for (Message m : request.messages()) {
            ObjectNode msgNode = messages.addObject();
            msgNode.put("role", m.role());
            msgNode.put("content", m.content());
        }
    }

    /**
     * 将 ChatRequest 中的 tools 写入 JSON body 的 "tools" 数组（无工具时不写入）。
     */
    static void appendTools(ObjectNode body, ChatRequest request, ObjectMapper mapper) throws Exception {
        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSchema tool : request.tools()) {
                ObjectNode toolNode = tools.addObject();
                toolNode.put("type", "function");
                ObjectNode function = toolNode.putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.set("parameters", mapper.readTree(tool.parameters()));
            }
        }
    }
}
