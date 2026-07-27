package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 号码混淆检测。检测手机号被顿号或空格分隔成 3+4+4 格式以规避平台检测。
 * rule_type: phone_obfuscation, weight: 0.3
 * 触发案例: 82（电话 "188、5593、6307" 顿号分隔）
 * 规则来自 104 条人工标注。
 */
@Component
public class PhoneObfuscationMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.3;

    /** 11位手机号被顿号或空格分成 3+4+4 格式 */
    private static final Pattern OBFUSCATED_PHONE_PATTERN = Pattern.compile(
            "1[3-9]\\d{1}[、\\s]\\d{4}[、\\s]\\d{4}");

    @Override
    public String ruleType() {
        return "phone_obfuscation";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }

        var matcher = OBFUSCATED_PHONE_PATTERN.matcher(body);
        if (matcher.find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "手机号被分隔符混淆: \"" + matcher.group() + "\""));
        }

        return Optional.empty();
    }
}
