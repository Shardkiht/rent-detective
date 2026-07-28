package io.github.shardkiht.rentdetective.eval.runner;

import java.util.List;

/**
 * 评估报告。规则来自 104 条人工标注，规则引擎为确定性打分。
 */
public record EvalReport(
        String groupName,
        int total,
        int correct,
        double accuracy,
        List<MisCase> misCases
) {

    /**
     * 错分案例明细。
     */
    public record MisCase(int id, String humanLabel, String predicted, String hitRules) {
    }
}
