package io.github.shardkiht.rentdetective.app.eval;

import java.util.List;

/**
 * 三方案对比评测报告。
 */
public record ComparisonReport(
        String strategy,
        int total,
        int correct,
        double overallAccuracy,
        List<GroupReport> groups
) {

    public record GroupReport(
            String groupName,
            int total,
            int correct,
            double accuracy,
            double reviewRate,
            List<MisCase> misCases
    ) {}

    public record MisCase(
            int listingId,
            String humanLabel,
            String predicted,
            String titleSnippet
    ) {}
}
