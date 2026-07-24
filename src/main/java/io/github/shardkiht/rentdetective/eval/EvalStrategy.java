package io.github.shardkiht.rentdetective.eval;

public interface EvalStrategy {

    String name();

    String evaluate(String expected, String actual);
}
