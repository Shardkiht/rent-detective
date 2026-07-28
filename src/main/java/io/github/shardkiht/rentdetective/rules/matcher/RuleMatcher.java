package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;

import java.util.Optional;

/**
 * 规则匹配器接口。每个实现对应一条 rule_type。
 */
public interface RuleMatcher {

    /** 对应的规则类型 */
    String ruleType();

    /** 对房源上下文进行匹配，命中则返回 RuleHit */
    Optional<RuleHit> match(ListingContext ctx);
}
