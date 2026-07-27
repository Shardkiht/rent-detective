package io.github.shardkiht.rentdetective.semantic.rule.matcher;

/**
 * 规则匹配命中结果。
 */
public record RuleHit(String ruleType, double weight, String evidence) {
}
