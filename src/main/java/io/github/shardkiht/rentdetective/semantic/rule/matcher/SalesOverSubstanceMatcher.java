package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 营销大于实质检测。大篇幅描述周边生活方式/景点/心情感受，房屋本身信息占比低于 30%。
 * rule_type: sales_over_substance, weight: 0.5
 * 触发案例: 15/50
 * 规则来自 104 条人工标注。
 */
@Component
public class SalesOverSubstanceMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.5;
    private static final double THRESHOLD = 0.3;

    /** 房屋硬信息关键词 */
    private static final Pattern HOUSE_INFO_PATTERN = Pattern.compile(
            "\\d室|\\d房|\\d厅|主卧|次卧|朝向|楼层|\\d+平|\\d+㎡|面积|装修|户型|采光|通风|电梯|阳台|卫生间|厨房|客厅|卧室|家具|家电|床|衣柜|书桌|空调|洗衣机|冰箱|热水器");

    /** 生活方式/营销描述关键词 */
    private static final Pattern LIFESTYLE_PATTERN = Pattern.compile(
            "商圈|商场|超市|医院|学校|公园|景点|地铁|公交|步行|分钟|生活圈|繁华|便利|热闹|美食|餐厅|咖啡|健身|娱乐|购物|休闲|舒适|惬意|享受|温馨|浪漫|时尚|潮流|打卡|网红|人气|口碑|推荐|必住|超值|划算|惊喜|心动");

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

        // 计算房屋硬信息字数
        int houseInfoLength = 0;
        var houseMatcher = HOUSE_INFO_PATTERN.matcher(body);
        while (houseMatcher.find()) {
            houseInfoLength += houseMatcher.group().length();
        }

        // 计算生活方式描述字数
        int lifestyleLength = 0;
        var lifestyleMatcher = LIFESTYLE_PATTERN.matcher(body);
        while (lifestyleMatcher.find()) {
            lifestyleLength += lifestyleMatcher.group().length();
        }

        // 房屋信息占比低于 30%
        double houseRatio = (double) houseInfoLength / body.length();
        if (houseRatio < THRESHOLD && lifestyleLength > 0) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    String.format("房屋信息占比 %.1f%%（低于 30%%），生活描述占比过高", houseRatio * 100)));
        }

        return Optional.empty();
    }
}
