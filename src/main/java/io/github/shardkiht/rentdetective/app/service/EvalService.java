package io.github.shardkiht.rentdetective.app.service;

import io.github.shardkiht.rentdetective.app.eval.EvalRunner;
import io.github.shardkiht.rentdetective.app.eval.report.EvalReport;
import org.springframework.stereotype.Service;

@Service
public class EvalService {

    private final EvalRunner evalRunner;

    public EvalService(EvalRunner evalRunner) {
        this.evalRunner = evalRunner;
    }

    public EvalReport runEval() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
