package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 纯联系方式正文检测。正文去掉手机号/微信号/常见填充词后几乎无实质内容。
 * rule_type: contact_only_body, weight: 0.2
 * 触发案例: 48（正文仅"为我13110758229"）, 55（正文仅"房东18268000684"）
 * 规则来自 104 条人工标注。
 */
@Component
public class ContactOnlyBodyMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.2;

    /** 正文实质内容最短长度（去掉联系方式后低于此值触发） */
    private static final int MIN_SUBSTANCE_LENGTH = 8;

    /** 正文总长度上限（超过此长度不可能是"纯联系方式"） */
    private static final int MAX_BODY_LENGTH = 40;

    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern FILLER_PATTERN = Pattern.compile(
            "[\\s\\p{So}赞]|详询|联系|看房|为我|房东|☎|🉑|➕|微信|加我|咨询");

    @Override
    public String ruleType() {
        return "contact_only_body";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty() || body.length() > MAX_BODY_LENGTH) {
            return Optional.empty();
        }

        // 必须含有联系方式（手机号或微信号）
        boolean hasPhoneInBody = PHONE_PATTERN.matcher(body).find();
        boolean hasWechatInBody = body.contains("微信") || body.contains("➕") || body.contains("加v");
        if (!hasPhoneInBody && !hasWechatInBody) {
            return Optional.empty();
        }

        // 去掉联系方式和填充词后检查剩余实质内容
        String stripped = PHONE_PATTERN.matcher(body).replaceAll("");
        stripped = FILLER_PATTERN.matcher(stripped).replaceAll("");

        if (stripped.length() < MIN_SUBSTANCE_LENGTH) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "正文仅含联系方式，无实质房源描述（剩余" + stripped.length() + "字）"));
        }

        return Optional.empty();
    }
}
