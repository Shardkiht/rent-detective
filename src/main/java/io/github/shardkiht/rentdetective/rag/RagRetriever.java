package io.github.shardkiht.rentdetective.rag;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 检索器 —— 委托 CaseVectorService 完成文本 → 相似案例 Top-K 检索。
 * <p>
 * Agent 循环手写，工具可插拔；识坑规则来自人工标注。
 */
@Component
public class RagRetriever {

    private final CaseVectorService caseVectorService;

    public RagRetriever(CaseVectorService caseVectorService) {
        this.caseVectorService = caseVectorService;
    }

    /**
     * 检索与给定文本最相似的 Top-K 历史案例。
     *
     * @param text  查询文本（通常是待调查房源的标题+描述）
     * @param topK  返回数量
     * @return 按相似度降序的 SearchHit 列表
     */
    public List<SearchHit> retrieve(String text, int topK) {
        List<SimilarCase> cases = caseVectorService.search(text, topK);
        return cases.stream()
                .map(sc -> {
                    SearchHit hit = new SearchHit();
                    hit.setId(String.valueOf(sc.listingId()));
                    hit.setContent(sc.title());
                    hit.setScore(sc.score());
                    return hit;
                })
                .toList();
    }
}
