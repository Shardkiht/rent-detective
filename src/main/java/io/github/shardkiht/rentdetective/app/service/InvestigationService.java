package io.github.shardkiht.rentdetective.app.service;

import io.github.shardkiht.rentdetective.agent.loop.ReActAgentLoop;
import io.github.shardkiht.rentdetective.agent.report.EvidenceChainReport;
import io.github.shardkiht.rentdetective.app.mapper.InvestigationTaskMapper;
import org.springframework.stereotype.Service;

@Service
public class InvestigationService {

    private final ReActAgentLoop agentLoop;
    private final InvestigationTaskMapper investigationTaskMapper;

    public InvestigationService(ReActAgentLoop agentLoop, InvestigationTaskMapper investigationTaskMapper) {
        this.agentLoop = agentLoop;
        this.investigationTaskMapper = investigationTaskMapper;
    }

    public EvidenceChainReport investigate(Long listingId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
