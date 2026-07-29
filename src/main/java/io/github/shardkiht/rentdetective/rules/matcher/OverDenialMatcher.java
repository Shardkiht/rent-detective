package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleHit;
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
    /** 正文超过此长度时，需要更多否认次数才触发（长文自然描述中偶发撇清不构成过度否认） */
    private static final int LONG_BODY_THRESHOLD = 200;
    private static final int LONG_BODY_COUNT = 3;

    /** 撇清话术关键词 */
    private static final Pattern DENIAL_PATTERN = Pattern.compile(
            "非中介|不是中介|非二房东|不赚任何差价|不赚差价|无套路|不是二房东|没有套路|无中介费|无任何.*中介");
    
    /** 房东直签豁免：含此模式表明否认有事实依据，不构成过度自证 */
    private static final Pattern DIRECT_SIGN_PATTERN = Pattern.compile(
            "和房东签|与房东签|跟房东签|房东直签|和原房东签|与原房东签");

    @Override
    public String ruleType() {
        return "over_denial";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String text = ctx.title() + " " + (ctx.body() != null ? ctx.body() : "");

        var matcher = DENIAL_PATTERN.matcher(text);
        int count = 0;
        StringBuilder hits = new StringBuilder();
        while (matcher.find()) {
            count++;
            if (!hits.isEmpty()) hits.append("、");
            hits.append(matcher.group());
        }

        if (count >= THRESHOLD) {
            // 长正文豁免：正文超过 200 字且否认次数未达 3 次，视为自然描述而非过度自证
            int bodyLen = ctx.body() != null ? ctx.body().length() : 0;
            if (bodyLen >= LONG_BODY_THRESHOLD && count < LONG_BODY_COUNT) {
                return Optional.empty();
            }
            // 房东直签豁免：提及与原房东直接签约，否认有事实依据，不构成过度自证
            if (DIRECT_SIGN_PATTERN.matcher(text).find()) {
                return Optional.empty();
            }
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "撇清话术出现" + count + "次: " + hits));
        }

        return Optional.empty();
    }
}
