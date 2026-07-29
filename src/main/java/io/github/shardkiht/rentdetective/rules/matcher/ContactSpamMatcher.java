package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleHit;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联系方式刷屏检测。同一联系方式（手机号或微信号）在正文中重复出现 ≥3 次。
 * rule_type: contact_spam, weight: 0.4
 * 触发案例: 24（同一电话重复 8 次）
 * 规则来自 104 条人工标注。
 */
@Component
public class ContactSpamMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.4;
    private static final int THRESHOLD = 3;

    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern WECHAT_PATTERN = Pattern.compile("微信[:：]\\s*(\\S+)");

    @Override
    public String ruleType() {
        return "contact_spam";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }

        // 统计手机号出现次数
        Map<String, Integer> phoneCounts = new HashMap<>();
        Matcher phoneMatcher = PHONE_PATTERN.matcher(body);
        while (phoneMatcher.find()) {
            String phone = phoneMatcher.group();
            phoneCounts.merge(phone, 1, Integer::sum);
        }

        // 检查是否有手机号重复 ≥3 次
        for (Map.Entry<String, Integer> entry : phoneCounts.entrySet()) {
            if (entry.getValue() >= THRESHOLD) {
                return Optional.of(new RuleHit(ruleType(), WEIGHT,
                        "手机号 " + entry.getKey() + " 重复出现 " + entry.getValue() + " 次"));
            }
        }

        // 统计微信号出现次数
        Map<String, Integer> wechatCounts = new HashMap<>();
        Matcher wechatMatcher = WECHAT_PATTERN.matcher(body);
        while (wechatMatcher.find()) {
            String wechat = wechatMatcher.group(1);
            wechatCounts.merge(wechat, 1, Integer::sum);
        }

        // 检查是否有微信号重复 ≥3 次
        for (Map.Entry<String, Integer> entry : wechatCounts.entrySet()) {
            if (entry.getValue() >= THRESHOLD) {
                return Optional.of(new RuleHit(ruleType(), WEIGHT,
                        "微信号 " + entry.getKey() + " 重复出现 " + entry.getValue() + " 次"));
            }
        }

        return Optional.empty();
    }
}
