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

    @Override
    public String ruleType() {
        return "self_disclosed_agent";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String nickname = ctx.nickname();
        if (nickname == null || nickname.isEmpty()) {
            return Optional.empty();
        }

        var matcher = AGENT_NICKNAME_PATTERN.matcher(nickname);
        if (matcher.find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "昵称含职业自曝词: \"" + nickname + "\""));
        }

        return Optional.empty();
    }
}
