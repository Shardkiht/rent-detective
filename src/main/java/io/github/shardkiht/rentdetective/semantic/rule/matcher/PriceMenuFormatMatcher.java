package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
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

        if (MULTI_PRICE_PATTERN.matcher(text).find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "检测到多个价格用分隔符罗列"));
        }

        return Optional.empty();
    }
}
