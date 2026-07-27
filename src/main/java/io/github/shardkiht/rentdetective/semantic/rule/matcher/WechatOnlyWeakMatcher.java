package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 仅微信无手机号检测。检测 phone 字段以"微信:"开头且不包含手机号格式数字。
 * rule_type: wechat_only_weak, weight: 0.2
 * 触发案例: 14/45/52/85/92/96/97
 */
@Component
public class WechatOnlyWeakMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.2;

    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern WECHAT_PREFIX_PATTERN = Pattern.compile("微信[:：]");

    @Override
    public String ruleType() {
        return "wechat_only_weak";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String phone = ctx.phone();
        if (phone == null || phone.isEmpty()) {
            return Optional.empty();
        }

        // phone 以"微信:"开头且不包含手机号格式数字
        if (WECHAT_PREFIX_PATTERN.matcher(phone).find() && !PHONE_NUMBER_PATTERN.matcher(phone).find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "仅提供微信号，无手机号: " + phone));
        }

        return Optional.empty();
    }
}
