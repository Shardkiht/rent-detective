package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 菜单式报价格式检测。检测多档报价格式，如"单间XXX+独卫XXX"/"一室XXX...两室XXX...三室XXX"。
 * rule_type: price_menu_format, weight: 0.7
 * 触发案例: 17/56/87
 */
@Component
public class PriceMenuFormatMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.7;

    /** 多档报价：连续出现多个 "户型/房型 + 价格" 模式 */
    private static final Pattern MENU_PATTERN = Pattern.compile(
            "(单间|独卫|整租|一室|两室|三室|主卧|次卧|开间).{0,5}\\d{3,}.{0,10}" +
            "(单间|独卫|整租|一室|两室|三室|主卧|次卧|开间).{0,5}\\d{3,}");

    /** 备选：用 + 或逗号分隔的多档价格 */
    private static final Pattern MULTI_PRICE_PATTERN = Pattern.compile(
            "\\d{3,}元?.{0,5}[+＋、,，].{0,5}\\d{3,}元?.{0,5}[+＋、,，].{0,5}\\d{3,}");

    /** 交通语境词：匹配到数字串前后 15 字符内出现这些词则排除 */
    private static final Pattern TRANSPORT_CONTEXT = Pattern.compile(
            "路|车|线|站|公交|号线|地铁|巴士| transit");

    @Override
    public String ruleType() {
        return "price_menu_format";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String text = ctx.title() + " " + ctx.body();

        if (MENU_PATTERN.matcher(text).find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "检测到多档报价格式（房型+价格多次出现）"));
        }

        Matcher multiMatcher = MULTI_PRICE_PATTERN.matcher(text);
        if (multiMatcher.find()) {
            // 排除交通语境：数字串前后 15 字符内有交通相关词则跳过
            int start = Math.max(0, multiMatcher.start() - 15);
            int end = Math.min(text.length(), multiMatcher.end() + 15);
            String context = text.substring(start, end);
            if (TRANSPORT_CONTEXT.matcher(context).find()) {
                return Optional.empty();
            }
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "检测到多个价格用分隔符罗列"));
        }

        return Optional.empty();
    }
}
