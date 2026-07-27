package io.github.shardkiht.rentdetective.semantic.engine;

import io.github.shardkiht.rentdetective.semantic.rule.matcher.RuleHit;

import java.util.List;

/**
 * 引擎评估结果。规则引擎为确定性打分，不涉及模型推理。
 */
public record EngineResult(
        Verdict verdict,
        double score,
        List<RuleHit> hits,
        List<String> advice,
        String reason
) {
}
