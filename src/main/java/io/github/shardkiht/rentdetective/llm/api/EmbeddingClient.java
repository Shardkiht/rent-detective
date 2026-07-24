package io.github.shardkiht.rentdetective.llm.api;

public interface EmbeddingClient {

    float[] embed(String text);
}
