package io.github.shardkiht.rentdetective.semantic.rule;

public interface RuleEngine {

    boolean match(String ruleName, Object context);
}
