package io.github.shardkiht.rentdetective.app.eval;

public interface EvalStrategy {

    String name();

    String evaluate(String expected, String actual);
}
