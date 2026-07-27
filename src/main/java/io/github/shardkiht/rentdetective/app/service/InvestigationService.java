package io.github.shardkiht.rentdetective.app.service;

import io.github.shardkiht.rentdetective.agent.loop.AgentStep;
import io.github.shardkiht.rentdetective.agent.loop.ReActAgentLoop;
import io.github.shardkiht.rentdetective.agent.report.EvidenceChainReport;
import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.app.mapper.ListingMapper;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 调查服务：调用 ReActAgentLoop 执行房源风险调查。
 * Agent 循环手写，工具可插拔；识坑规则来自人工标注，在 semantic 包。
 */
@Service
public class InvestigationService {

    private final ReActAgentLoop agentLoop;
    private final ListingMapper listingMapper;

    public InvestigationService(ReActAgentLoop agentLoop, ListingMapper listingMapper) {
        this.agentLoop = agentLoop;
        this.listingMapper = listingMapper;
    }

    /**
     * 根据 listingId 从数据库查询并执行调查（无回调）。
     */
    public EvidenceChainReport investigate(Long listingId) {
        Listing listing = listingMapper.selectById(listingId);
        if (listing == null) {
            throw new IllegalArgumentException("Listing not found: " + listingId);
        }
        return agentLoop.investigate(listing);
    }

    /**
     * 直接对给定 Listing 执行调查，支持 step 回调（用于 SSE 推送）。
     */
    public EvidenceChainReport investigate(Listing listing, Consumer<AgentStep> onStep) {
        return agentLoop.investigate(listing, onStep);
    }
}
