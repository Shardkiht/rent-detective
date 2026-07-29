package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleHit;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 身份混用检测。同一条里同时出现职业化合租运营用词和个人房东用词。
 * rule_type: identity_mixed, weight: 0.5
 * 触发案例: 12
 * 规则来自 104 条人工标注。
 */
@Component
public class IdentityMixedMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.5;

    /** 职业化合租运营用词 */
    private static final Pattern PROFESSIONAL_PATTERN = Pattern.compile(
            "全女生|全男生|纯女生|纯男生|女生合租|男生合租");

    /** 个人房东用词 */
    private static final Pattern PERSONAL_PATTERN = Pattern.compile(
            "自家房子|自住直租|房东直租|个人直租|自己房子|本人房东");

    @Override
    public String ruleType() {
        return "identity_mixed";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String text = ctx.title() + " " + ctx.body();

        boolean hasProfessional = PROFESSIONAL_PATTERN.matcher(text).find();
        boolean hasPersonal = PERSONAL_PATTERN.matcher(text).find();

        if (hasProfessional && hasPersonal) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "身份混用：同时含职业化合租用词和个人房东用词"));
        }

        return Optional.empty();
    }
}
