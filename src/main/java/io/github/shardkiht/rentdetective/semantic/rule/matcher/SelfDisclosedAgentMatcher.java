package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 职业自曝检测。检测昵称含"租赁/中介/小能手/管家/租房小能手"等职业自曝词。
 * rule_type: self_disclosed_agent, weight: 0.8
 * 触发案例: 24/66
 * 注意：只检查 nickname 字段，不检查 body。
 */
@Component
public class SelfDisclosedAgentMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.8;

    private static final Pattern AGENT_NICKNAME_PATTERN = Pattern.compile(
            "租赁|中介|小能手|管家|租房小能手|房产顾问|租房顾问|置业顾问|房屋管家");

    /** 正文中的中介身份关键词（如“公寓直租”） */
    private static final Pattern AGENT_BODY_PATTERN = Pattern.compile(
            "公寓直租|房源直租");
    
    /** 描述头部括号式中介标识（如"(住宅租赁15557143558)"），排除否定式如"(无中介费)" */
    private static final Pattern AGENT_PAREN_HEADER_PATTERN = Pattern.compile(
            "[（(][^）)]*(?:租赁|(?<![无非])中介|公寓|房产|置业)[^）)]*[）)]");

    @Override
    public String ruleType() {
        return "self_disclosed_agent";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        // 1. 检查昵称
        String nickname = ctx.nickname();
        if (nickname != null && !nickname.isEmpty()) {
            var matcher = AGENT_NICKNAME_PATTERN.matcher(nickname);
            if (matcher.find()) {
                return Optional.of(new RuleHit(ruleType(), WEIGHT,
                        "昵称含职业自曝词: \"" + nickname + "\""));
            }
        }
    
        // 2. 检查正文中的中介身份关键词（如“公寓直租”）
        String body = ctx.body();
        if (body != null && !body.isEmpty()) {
            var bodyMatcher = AGENT_BODY_PATTERN.matcher(body);
            if (bodyMatcher.find()) {
                return Optional.of(new RuleHit(ruleType(), WEIGHT,
                        "正文含中介身份关键词: \"" + bodyMatcher.group() + "\""));
            }
        }
    
        // 3. 检查描述头部括号式中介标识（如“(住宅租赁15557143558)”）
        String desc = ctx.description();
        if (desc != null && !desc.isEmpty()) {
            var parenMatcher = AGENT_PAREN_HEADER_PATTERN.matcher(desc);
            if (parenMatcher.find()) {
                return Optional.of(new RuleHit(ruleType(), WEIGHT,
                        "头部含中介标识: \"" + parenMatcher.group() + "\""));
            }
        }
    
        return Optional.empty();
    }
}
