package io.github.shardkiht.rentdetective.llm;

public interface EmbeddingClient {

    float[] embed(String text);
}
