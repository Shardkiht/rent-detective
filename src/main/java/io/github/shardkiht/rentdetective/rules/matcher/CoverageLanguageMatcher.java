package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleHit;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 覆盖语言检测。检测正文罗列3条以上地铁线路站点，或含"多套出租"/"附近多套"/"都有房源"等。
 * rule_type: coverage_language, weight: 0.6
 * 触发案例: 53/56/42/89/90
 */
@Component
public class CoverageLanguageMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.6;

    /** 提取线路编号（用于去重） */
    private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile(
            "([一二三四五六七八九十\\d]+)号线|地铁([一二三四五六七八九十\\d]+)");

    /** 多套/覆盖语关键词 */
    private static final Pattern MULTI_LISTING_PATTERN = Pattern.compile(
            "多套出租|附近多套|都有房源|多套房源|还有.*套|本小区.*套");

    @Override
    public String ruleType() {
        return "coverage_language";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String text = ctx.title() + " " + (ctx.body() != null ? ctx.body() : "");

        // 检测地铁线路罗列（3条以上不同线路）
        Set<String> uniqueLines = new HashSet<>();
        var lineMatcher = LINE_NUMBER_PATTERN.matcher(text);
        while (lineMatcher.find()) {
            String num = lineMatcher.group(1) != null ? lineMatcher.group(1) : lineMatcher.group(2);
            uniqueLines.add(normalizeChineseNumeral(num));
        }
        if (uniqueLines.size() >= 3) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "正文罗列" + uniqueLines.size() + "条不同地铁线路"));
        }

        // 检测多套/覆盖语
        var multiMatcher = MULTI_LISTING_PATTERN.matcher(text);
        if (multiMatcher.find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "含覆盖语关键词: " + multiMatcher.group()));
        }

        return Optional.empty();
    }

    /** 中文数字转阿拉伯数字（用于线路去重） */
    private static String normalizeChineseNumeral(String s) {
        return switch (s) {
            case "一" -> "1";
            case "二" -> "2";
            case "三" -> "3";
            case "四" -> "4";
            case "五" -> "5";
            case "六" -> "6";
            case "七" -> "7";
            case "八" -> "8";
            case "九" -> "9";
            case "十" -> "10";
            default -> s;
        };
    }
}
