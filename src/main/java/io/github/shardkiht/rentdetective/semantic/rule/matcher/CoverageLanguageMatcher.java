package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 覆盖语言检测。检测正文罗列3条以上地铁线路站点，或含"多套出租"/"附近多套"/"都有房源"等。
 * rule_type: coverage_language, weight: 0.6
 * 触发案例: 53/56/42/89/90
 */
@Component
public class CoverageLanguageMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.6;

    /** 地铁线路关键词 */
    private static final Pattern METRO_PATTERN = Pattern.compile(
            "(\\d号线|[一二三四五六七八九十]+号线|地铁[一二三四五六七八九十\\d]+)");

    /** 多套/覆盖语关键词 */
    private static final Pattern MULTI_LISTING_PATTERN = Pattern.compile(
            "多套出租|附近多套|都有房源|多套房源|还有.*套|本小区.*套");

    @Override
    public String ruleType() {
        return "coverage_language";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }

        // 检测地铁线路罗列（3条以上）
        var metroMatcher = METRO_PATTERN.matcher(body);
        int metroCount = 0;
        while (metroMatcher.find()) {
            metroCount++;
        }
        if (metroCount >= 3) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "正文罗列" + metroCount + "条地铁线路站点"));
        }

        // 检测多套/覆盖语
        var multiMatcher = MULTI_LISTING_PATTERN.matcher(body);
        if (multiMatcher.find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "含覆盖语关键词: " + multiMatcher.group()));
        }

        return Optional.empty();
    }
}
