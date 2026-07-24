package io.github.shardkiht.rentdetective.agent.report;

import java.util.List;

public class EvidenceChainReport {

    private String verdict;
    private double confidence;
    private List<Evidence> evidences;

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<Evidence> getEvidences() {
        return evidences;
    }

    public void setEvidences(List<Evidence> evidences) {
        this.evidences = evidences;
    }

    public static class Evidence {
        private String claim;
        private String sourceTool;
        private String sourceCase;
        private String quote;

        public String getClaim() {
            return claim;
        }

        public void setClaim(String claim) {
            this.claim = claim;
        }

        public String getSourceTool() {
            return sourceTool;
        }

        public void setSourceTool(String sourceTool) {
            this.sourceTool = sourceTool;
        }

        public String getSourceCase() {
            return sourceCase;
        }

        public void setSourceCase(String sourceCase) {
            this.sourceCase = sourceCase;
        }

        public String getQuote() {
            return quote;
        }

        public void setQuote(String quote) {
            this.quote = quote;
        }
    }
}
