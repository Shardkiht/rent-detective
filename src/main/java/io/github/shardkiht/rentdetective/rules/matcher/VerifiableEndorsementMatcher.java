package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleHit;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 可验证背书检测（正向规则）。出现"出示房产证"/"可提供合同核实"等可验证的具体承诺，降低风险分。
 * rule_type: verifiable_endorsement, weight: -0.5（负权重，降低总分）
 * 触发案例: 3（"出示房产证原件"）, 44（"可提供原始租赁合同核实"）
 * 规则来自 104 条人工标注。
 */
@Component
public class VerifiableEndorsementMatcher implements RuleMatcher {

    private static final double WEIGHT = -0.5;

    /** 可验证的具体承诺（要求完整语义，单个名词不命中） */
    private static final Pattern VERIFIABLE_PATTERN = Pattern.compile(
            "出示房产证|提供房产证原件|可提供.*合同核实|提供原始租赁合同.*核实|押一付.*明细账单|可验证.*房产证|房产证原件.*可看");

    @Override
    public String ruleType() {
        return "verifiable_endorsement";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }

        var matcher = VERIFIABLE_PATTERN.matcher(body);
        if (matcher.find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "可验证背书: \"" + matcher.group() + "\""));
        }

        return Optional.empty();
    }
}
