package io.github.shardkiht.rentdetective.agent.report;

import io.github.shardkiht.rentdetective.agent.loop.AgentStep;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class EvidenceChainReport {

    private String verdict;
    private double confidence;
    private List<Evidence> evidences;
    private boolean converged;
    private List<AgentStep> trace;

    @Setter
    @Getter
    public static class Evidence {
        private String claim;
        private String sourceTool;
        private String sourceCase;
        private String quote;

    }
}
