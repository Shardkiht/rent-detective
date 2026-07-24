package io.github.shardkiht.rentdetective.agent.loop;

import io.github.shardkiht.rentdetective.agent.report.EvidenceChainReport;
import io.github.shardkiht.rentdetective.agent.tool.ToolRegistry;
import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.ChatResponse;
import io.github.shardkiht.rentdetective.llm.LLMClient;
import org.springframework.stereotype.Component;

@Component
public class ReActAgentLoop {

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;

    public ReActAgentLoop(LLMClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
    }

    public EvidenceChainReport investigate(String listingJson) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
