package io.github.shardkiht.rentdetective.llm.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.shardkiht.rentdetective.llm.*;
import io.github.shardkiht.rentdetective.llm.api.LLMClient;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class OpenAiCompatibleLLMClient implements LLMClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OpenAiCompatibleLLMClient(
            @Value("${rentdetective.llm.openai-compatible.base-url}") String baseUrl,
            @Value("${rentdetective.llm.openai-compatible.api-key}") String apiKey,
            @Value("${rentdetective.llm.openai-compatible.model}") String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", request.temperature());

            ArrayNode messages = body.putArray("messages");
            for (Message m : request.messages()) {
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", m.role());
                msgNode.put("content", m.content());
            }

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

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(mapper.writeValueAsBytes(body), LlmClientConstants.JSON_MEDIA_TYPE))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new LLMException("云端 API 请求失败，HTTP " + response.code());
                }
                JsonNode root = mapper.readTree(response.body().string());
                JsonNode messageNode = root.path("choices").path(0).path("message");
                String content = messageNode.path("content").asText("");

                ChatResponse.ToolCall toolCall = null;
                boolean hasToolCall = false;
                JsonNode toolCalls = messageNode.path("tool_calls");
                if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                    JsonNode fn = toolCalls.get(0).path("function");
                    toolCall = new ChatResponse.ToolCall(fn.path("name").asText(), fn.path("arguments").asText());
                    hasToolCall = true;
                }

                return ChatResponse.builder()
                        .content(content)
                        .hasToolCall(hasToolCall)
                        .toolCall(toolCall)
                        .degraded(false)
                        .build();
            }
        } catch (IOException e) {
            throw new LLMException("调用云端 API 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String engineName() {
        return "cloud-" + model;
    }
}