package io.github.shardkiht.rentdetective.rag;

import io.github.shardkiht.rentdetective.llm.api.EmbeddingClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagRetriever {

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;

    public RagRetriever(VectorStore vectorStore, EmbeddingClient embeddingClient) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
    }

    public List<SearchHit> retrieve(String text, int topK) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
