package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 过度否认检测。检测撇清话术（"非中介"/"非二房东"/"不赚差价"/"无套路"等）在正文中出现 ≥2 次。
 * rule_type: over_denial, weight: 0.6
 * 触发案例: 8/43/87/99/101
 * 注意：感叹号密度单独不构成过度自证（陷阱 #3）。"我是房东！！！"是热情不是话术。
 * 规则来自 104 条人工标注。
 */
@Component
public class OverDenialMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.6;
    private static final int THRESHOLD = 2;

    /** 撇清话术关键词 */
    private static final Pattern DENIAL_PATTERN = Pattern.compile(
            "非中介|不是中介|非二房东|不赚任何差价|不赚差价|无套路|不是二房东|没有套路|不是二房东");

    @Override
    public String ruleType() {
        return "over_denial";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }

        var matcher = DENIAL_PATTERN.matcher(body);
        int count = 0;
        StringBuilder hits = new StringBuilder();
        while (matcher.find()) {
            count++;
            if (hits.length() > 0) hits.append("、");
            hits.append(matcher.group());
        }

        if (count >= THRESHOLD) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "撇清话术出现" + count + "次: " + hits));
        }

        return Optional.empty();
    }
}
