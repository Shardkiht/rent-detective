package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 营销大于实质检测（重写版）。
 * 触发条件：营销废话占比 > 15% 且 结构化信息（数字+单位）少于 3 处。
 * 反转逻辑：不再试图穷举"房屋信息关键词"，而是检测纯营销话术是否过多且缺乏实质数据支撑。
 * rule_type: sales_over_substance, weight: 0.5
 */
@Component
public class SalesOverSubstanceMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.5;

    /** 营销废话占比阈值 */
    private static final double MARKETING_RATIO_THRESHOLD = 0.15;

    /** 结构化信息最少处数（低于此值才触发） */
    private static final int MIN_STRUCTURED_INFO = 3;

    /**
     * 纯营销/情绪话术关键词（可穷举，数量远少于"房屋信息"种类）。
     * 不含交通/地标等中性词——那些是位置描述，不是营销。
     */
    private static final Pattern MARKETING_PATTERN = Pattern.compile(
            "温馨|浪漫|舒适|舒服|惬意|享受|时尚|潮流|打卡|网红|人气|口碑|必住|超值|划算|" +
            "惊喜|心动|不容错过|错过后悔|手慢无|抢手|火爆|热销|秒杀|限时|特价|" +
            "拎包入住|管家式|酒店式|轻奢|高端|豪华|精装豪宅|品质生活|理想居所|" +
            "家的感觉|如归|暖心|贴心|尊享|私密|静谧|悠然|宜居|臻品");

    /**
     * 结构化信息：数字 + 单位/量词组合。
     * 匹配如：1580/月、6楼、8层、300M宽带、押一付三、2室1厅、89㎡、100平、4/10号线
     */
    private static final Pattern STRUCTURED_INFO_PATTERN = Pattern.compile(
            "\\d+[\\d./]*\\s*(元|月|楼|层|平|㎡|m²|M|兆|室|房|厅|卫|厨|号线|号线|年|天|" +
            "公里|km|分钟|小时|宽带|兆|G|押\\d付\\d|季付|月付|年付)");

    @Override
    public String ruleType() {
        return "sales_over_substance";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String body = ctx.body();
        if (body == null || body.length() < 50) {
            return Optional.empty();
        }

        // 1. 计算营销废话字数占比
        int marketingLength = 0;
        Matcher mktMatcher = MARKETING_PATTERN.matcher(body);
        while (mktMatcher.find()) {
            marketingLength += mktMatcher.group().length();
        }
        double marketingRatio = (double) marketingLength / body.length();

        // 营销废话占比不够高 → 不触发
        if (marketingRatio < MARKETING_RATIO_THRESHOLD) {
            return Optional.empty();
        }

        // 2. 计算结构化信息处数
        int structuredCount = 0;
        Matcher structMatcher = STRUCTURED_INFO_PATTERN.matcher(body);
        while (structMatcher.find()) {
            structuredCount++;
        }

        // 结构化信息充足 → 有实质内容支撑，不触发
        if (structuredCount >= MIN_STRUCTURED_INFO) {
            return Optional.empty();
        }

        return Optional.of(new RuleHit(ruleType(), WEIGHT,
                String.format("营销话术占比 %.1f%%（>15%%），结构化信息仅 %d 处（<%d），缺乏实质内容",
                        marketingRatio * 100, structuredCount, MIN_STRUCTURED_INFO)));
    }
}
