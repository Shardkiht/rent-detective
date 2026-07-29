package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleHit;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 人设错位检测。正文开头口语化/年轻化，但后续出现专业中介术语。
 * rule_type: persona_mismatch, weight: 0.5
 * 触发案例: 35（"转租呀"软妹口吻 + "民水电包网包物业"中介话术）
 * 规则来自 104 条人工标注。
 */
@Component
public class PersonaMismatchMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.5;

    /** 口语化/年轻化开头关键词 */
    private static final Pattern CASUAL_PATTERN = Pattern.compile(
            "呀|软妹|超爱|太舒服了|宝子们|姐妹们|真的超|好喜欢|爱了爱了|转租呀");

    /** 专业中介术语 */
    private static final Pattern PROFESSIONAL_PATTERN = Pattern.compile(
            "民水电|包网包物业|押一付一|押一付三|押一付|物业费|水电网全包|拎包入住|精装修|南北通透");

    @Override
    public String ruleType() {
        return "persona_mismatch";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String body = ctx.body();
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }

        // 检查前100字是否有口语化表达
        String head = body.length() > 100 ? body.substring(0, 100) : body;
        boolean hasCasual = CASUAL_PATTERN.matcher(head).find();

        // 检查后续是否有专业术语
        boolean hasProfessional = PROFESSIONAL_PATTERN.matcher(body).find();

        if (hasCasual && hasProfessional) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "人设错位：口语化开头+专业中介术语"));
        }

        return Optional.empty();
    }
}
