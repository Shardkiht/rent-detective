package io.github.shardkiht.rentdetective.llm;

public interface StreamCallback {

    void onChunk(String chunk);

    void onComplete();

    void onError(Throwable throwable);
}
