package io.github.shardkiht.rentdetective.app.controller;

import io.github.shardkiht.rentdetective.app.service.InvestigationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investigate")
public class InvestigateController {

    private final InvestigationService investigationService;

    public InvestigateController(InvestigationService investigationService) {
        this.investigationService = investigationService;
    }
}
