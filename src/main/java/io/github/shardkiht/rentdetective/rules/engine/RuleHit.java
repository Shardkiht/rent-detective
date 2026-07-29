package io.github.shardkiht.rentdetective.rules.engine;

/**
 * 规则匹配命中结果。
 */
public record RuleHit(String ruleType, double weight, String evidence) {
}
