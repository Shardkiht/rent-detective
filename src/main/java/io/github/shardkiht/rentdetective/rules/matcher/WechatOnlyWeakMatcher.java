package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
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
    /** 正文中的微信号模式（如"➕v"、"➕微信"、"联系方式：XXX"无手机号） */
    private static final Pattern WECHAT_BODY_PATTERN = Pattern.compile(
            "➕\\s*v\\b|➕\\s*微信|加\\s*v\\b|加\\s*微信|联系方式[：:]\\s*(?=[A-Za-z0-9_]{3,}.*[A-Za-z_])[A-Za-z0-9_]{3,}");

    @Override
    public String ruleType() {
        return "wechat_only_weak";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String phone = ctx.phone();
        String text = ctx.title() + " " + (ctx.body() != null ? ctx.body() : "");

        // 1. phone 字段以"微信:"开头且不包含手机号格式数字
        if (phone != null && !phone.isEmpty()
                && WECHAT_PREFIX_PATTERN.matcher(phone).find()
                && !PHONE_NUMBER_PATTERN.matcher(phone).find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "仅提供微信号，无手机号: " + phone));
        }

        // 2. 正文中含微信号但无手机号（如"➕v"、"联系方式：Q0506H_"）
        if (PHONE_NUMBER_PATTERN.matcher(text).find()) {
            return Optional.empty(); // 有手机号，不算“仅微信”
        }
        var bodyWechat = WECHAT_BODY_PATTERN.matcher(text);
        if (bodyWechat.find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "正文含微信号但无手机号: " + bodyWechat.group()));
        }

        return Optional.empty();
    }
}
