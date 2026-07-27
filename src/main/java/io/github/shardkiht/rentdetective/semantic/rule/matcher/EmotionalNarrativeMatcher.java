package io.github.shardkiht.rentdetective.semantic.rule.matcher;

import io.github.shardkiht.rentdetective.semantic.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 情感叙事检测。检测正文包含情感化描写，但同时缺少小区名/价格/联系方式/房间细节中的 ≥2 项。
 * 必须同时满足"有情感词"+"缺细节"两个条件。
 * rule_type: emotional_narrative, weight: 0.6
 * 触发案例: 50/61/94/98
 * 规则来自 104 条人工标注。
 */
@Component
public class EmotionalNarrativeMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.6;
    private static final int MISSING_THRESHOLD = 2;

    /** 情感化描写关键词 */
    private static final Pattern EMOTIONAL_PATTERN = Pattern.compile(
            "舍不得|有感情|太舒服|忍疼|不爱出门|温馨|治愈|幸福感|满满的回忆|离开这里|住在这里.*舒服");

    /** 小区名检测 */
    private static final Pattern COMMUNITY_PATTERN = Pattern.compile(
            "小区|公寓|花园|苑|村|坊|家园|名都|世纪|大厦|广场|新城|嘉园|雅苑");

    /** 房间细节关键词 */
    private static final Pattern ROOM_DETAIL_PATTERN = Pattern.compile(
            "\\d室|\\d房|\\d厅|主卧|次卧|朝向|楼层|\\d+平|\\d+㎡|面积|装修");

    @Override
    public String ruleType() {
        return "emotional_narrative";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }

        // 条件1：有情感词
        if (!EMOTIONAL_PATTERN.matcher(body).find()) {
            return Optional.empty();
        }

        // 条件2：缺细节 ≥2 项
        int missingCount = 0;

        if (!ctx.hasPrice()) missingCount++;
        if (!ctx.hasContact()) missingCount++;

        // 缺小区名
        if (!COMMUNITY_PATTERN.matcher(body).find()) missingCount++;

        // 缺房间细节
        if (!ROOM_DETAIL_PATTERN.matcher(body).find()) missingCount++;

        if (missingCount >= MISSING_THRESHOLD) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "情感化描写但缺失" + missingCount + "项关键信息"));
        }

        return Optional.empty();
    }
}
