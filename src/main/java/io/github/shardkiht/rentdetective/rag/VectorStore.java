package io.github.shardkiht.rentdetective.rag;

import java.util.List;

public interface VectorStore {

    void save(RagDocument document);

    List<SearchHit> search(float[] vector, int topK, String filter);
}
