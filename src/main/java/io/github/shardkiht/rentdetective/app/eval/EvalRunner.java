package io.github.shardkiht.rentdetective.app.eval;

import io.github.shardkiht.rentdetective.app.eval.report.EvalReport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EvalRunner {

    private final List<EvalStrategy> strategies;

    public EvalRunner(List<EvalStrategy> strategies) {
        this.strategies = strategies;
    }

    public EvalReport run(List<String> cases) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
