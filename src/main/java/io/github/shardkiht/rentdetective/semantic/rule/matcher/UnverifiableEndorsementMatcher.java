package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 不可验证背书检测。出现"开发商自持"/"品牌公寓"/"品质公寓"等背书词，但不给出具体品牌名/公寓名。
 * rule_type: unverifiable_endorsement, weight: 0.4
 * 触发案例: 29（"开发商自持"但无品牌名）, 102
 * 规则来自 104 条人工标注。
 */
@Component
public class UnverifiableEndorsementMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.4;

    /** 背书词 */
    private static final Pattern ENDORSEMENT_PATTERN = Pattern.compile(
            "开发商自持|品牌公寓|品质公寓|高端公寓|精品公寓|知名开发商|品牌开发商");

    /** 具体品牌名——真实品牌公寓会主动写出品牌名 */
    private static final Pattern BRAND_PATTERN = Pattern.compile(
            "万科|龙湖冠寓|碧桂园|恒大|保利|中海|融创|华润|招商蛇口|金地|旭辉|新城控股|阳光城|绿城|远洋|世茂|龙光|金茂|中骏|正荣|雅居乐|美的置业|荣盛|中南|建业|佳兆业|越秀|合景泰富|朗诗|魔方公寓|自如|蛋壳|相寓|泊寓|冠寓|城家|窝趣|YOU\\+|Base|Q\\+");

    @Override
    public String ruleType() {
        return "unverifiable_endorsement";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String text = ctx.title() + " " + ctx.body();

        // 有背书词
        if (!ENDORSEMENT_PATTERN.matcher(text).find()) {
            return Optional.empty();
        }

        // 没有具体品牌名
        if (!BRAND_PATTERN.matcher(text).find()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "出现背书词但未提供具体品牌名"));
        }

        return Optional.empty();
    }
}
