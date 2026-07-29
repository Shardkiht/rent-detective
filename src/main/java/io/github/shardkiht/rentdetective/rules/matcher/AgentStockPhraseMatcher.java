package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleHit;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 中介套话检测。分三档：
 * - 强词命中（"看房热线"/"看房联系管家"/"今日特价"）→ weight 0.6
 * - 弱词命中≥2个（"随时看房"/"拎包入住"/"家电齐全"/"精装修"）→ weight 0.3
 * - 弱词命中1个 → weight 0.1
 * rule_type: agent_stock_phrase
 * 触发案例: 63/82/67
 */
@Component
public class AgentStockPhraseMatcher implements RuleMatcher {

    private static final double STRONG_WEIGHT = 0.6;
    private static final double WEAK_MULTI_WEIGHT = 0.3;
    private static final double WEAK_SINGLE_WEIGHT = 0.1;

    private static final Pattern STRONG_PATTERN = Pattern.compile(
            "看房热线|看房联系管家|今日特价");

    private static final String[] WEAK_PHRASES = {"随时看房", "拎包入住", "好房推荐", "无敌性价比", "通勤党狂喜"};

    @Override
    public String ruleType() {
        return "agent_stock_phrase";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String text = ctx.title() + " " + ctx.body();

        // 强词检测
        var strongMatcher = STRONG_PATTERN.matcher(text);
        if (strongMatcher.find()) {
            return Optional.of(new RuleHit(ruleType(), STRONG_WEIGHT,
                    "命中强套话: \"" + strongMatcher.group() + "\""));
        }

        // 弱词检测
        int weakCount = 0;
        StringBuilder weakHits = new StringBuilder();
        for (String weak : WEAK_PHRASES) {
            if (text.contains(weak)) {
                weakCount++;
                if (!weakHits.isEmpty()) weakHits.append("、");
                weakHits.append(weak);
            }
        }

        if (weakCount >= 2) {
            return Optional.of(new RuleHit(ruleType(), WEAK_MULTI_WEIGHT,
                    "命中" + weakCount + "个弱套话: " + weakHits));
        } else if (weakCount == 1) {
            return Optional.of(new RuleHit(ruleType(), WEAK_SINGLE_WEIGHT,
                    "命中1个弱套话: " + weakHits));
        }

        return Optional.empty();
    }
}
