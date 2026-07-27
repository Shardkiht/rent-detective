package io.github.shardkiht.rentdetective.app.service;

import io.github.shardkiht.rentdetective.semantic.eval.EvalRunner;
import io.github.shardkiht.rentdetective.semantic.eval.EvalReport;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EvalService {

    private final EvalRunner evalRunner;

    public EvalService(EvalRunner evalRunner) {
        this.evalRunner = evalRunner;
    }

    public List<EvalReport> runEval() throws Exception {
        return evalRunner.run(null);
    }
}
