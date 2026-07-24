package io.github.shardkiht.rentdetective.llm.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.shardkiht.rentdetective.llm.api.EmbeddingClient;
import io.github.shardkiht.rentdetective.llm.LLMException;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String embeddingModel;

    public OllamaEmbeddingClient(
            @Value("${rentdetective.llm.ollama.base-url}") String baseUrl,
            @Value("${rentdetective.llm.ollama.embedding-model:bge-m3}") String embeddingModel) {
        this.baseUrl = baseUrl;
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
            body.put("prompt", text);

            Request request = new Request.Builder()
                    .url(baseUrl + "/api/embeddings")
                    .post(RequestBody.create(mapper.writeValueAsBytes(body), JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new LLMException("Ollama embedding 请求失败，HTTP " + response.code());
                }
                JsonNode vectorNode = mapper.readTree(response.body().string()).path("embedding");
                float[] vector = new float[vectorNode.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) vectorNode.get(i).asDouble();
                }
                return vector;
            }
        } catch (IOException e) {
            throw new LLMException("调用 Ollama embedding 失败: " + e.getMessage(), e);
        }
    }
}