package io.github.shardkiht.rentdetective.app.task;

import io.github.shardkiht.rentdetective.app.service.InvestigationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class InvestigationTaskExecutor {

    private final InvestigationService investigationService;

    public InvestigationTaskExecutor(InvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    @Async("investigationExecutor")
    public void submit(Long listingId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
