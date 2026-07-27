package io.github.shardkiht.rentdetective.rag;

/**
 * Top-K 检索返回的相似案例。
 *
 * @param listingId  房源 ID
 * @param title      标题
 * @param riskLevel  人工标注的风险等级（来自 listings 表）
 * @param riskTags   人工标注的风险标签（来自 listings 表）
 * @param score      余弦相似度，0~1
 * @param reason     机械模板生成的相似原因（不允许 LLM 生成）
 */
public record SimilarCase(
        int listingId,
        String title,
        String riskLevel,
        String riskTags,
        double score,
        String reason
) {
}
