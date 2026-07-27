package io.github.shardkiht.rentdetective.llm.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.shardkiht.rentdetective.llm.LLMException;
import io.github.shardkiht.rentdetective.llm.api.EmbeddingClient;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String embeddingModel;

    public OpenAiCompatibleEmbeddingClient(
            @Value("${rentdetective.llm.openai-compatible.base-url}") String baseUrl,
            @Value("${rentdetective.llm.openai-compatible.api-key}") String apiKey,
            @Value("${rentdetective.llm.openai-compatible.embedding-model:text-embedding-v3}") String embeddingModel) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public float[] embed(String text) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", embeddingModel);
            body.put("input", text);

            Request request = new Request.Builder()
                    .url(baseUrl + "/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(mapper.writeValueAsBytes(body), LlmClientConstants.JSON_MEDIA_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new LLMException("云端 embedding 请求失败，HTTP " + response.code());
                }
                JsonNode vectorNode = mapper.readTree(response.body().string())
                        .path("data").path(0).path("embedding");
                float[] vector = new float[vectorNode.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) vectorNode.get(i).asDouble();
                }
                return vector;
            }
        } catch (IOException e) {
            throw new LLMException("调用云端 embedding 失败: " + e.getMessage(), e);
        }
    }
}
