package io.github.shardkiht.rentdetective.semantic.engine;

/**
 * 引擎判定结果。规则引擎为确定性打分，不涉及模型推理。
 */
public enum Verdict {
    /** 安全 */
    SAFE,
    /** 可疑（score >= suspiciousThreshold） */
    SUSPICIOUS,
    /** 需人工复核（score >= reviewThreshold） */
    REVIEW,
    /** 证据不足 */
    INSUFFICIENT,
    /** 非房源 */
    NOT_LISTING
}
