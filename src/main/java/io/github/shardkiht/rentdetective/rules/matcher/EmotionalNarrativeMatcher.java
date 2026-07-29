package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleHit;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 情感叙事检测。标题/正文含情感话术（"太舒服/舍不得/有感情"等）但正文缺乏实质细节。
 * rule_type: emotional_narrative, weight: 0.6
 * 触发案例: 61（标题"因为家里太舒服所以真的不想出门"，正文仅联系方式）
 * 规则来自 104 条人工标注。
 */
@Component
public class EmotionalNarrativeMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.6;

    /** 情感话术关键词 */
    private static final Pattern EMOTION_PATTERN = Pattern.compile(
            "太舒服|舍不得|有感情|不想出门|太温馨|太幸福|爱了|心动|恋恋不舍|依依不舍");

    /** 正文实质内容最短长度（低于此值视为细节缺失） */
    private static final int MIN_SUBSTANCE_LENGTH = 30;

    @Override
    public String ruleType() {
        return "emotional_narrative";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String title = ctx.title() != null ? ctx.title() : "";
        String body = ctx.body() != null ? ctx.body() : "";

        // 情感词检测（标题或正文）
        String titleAndBody = title + " " + body;
        var matcher = EMOTION_PATTERN.matcher(titleAndBody);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String emotionWord = matcher.group();

        // 细节缺失检测：正文过短（去掉联系方式后无实质内容）
        String stripped = body.replaceAll("1[3-9]\\d{9}", "")
                .replaceAll("[\\s\\p{So}赞]", "")
                .replaceAll("详询|联系|看房|☎|🉑|➕", "");
        if (stripped.length() < MIN_SUBSTANCE_LENGTH) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "情感话术\"" + emotionWord + "\"但正文仅联系方式，缺乏实质细节"));
        }

        // 情感话术 + 无价格无联系方式：即使正文较长，纯情感叙事无任何实质信息仍触发
        if (!ctx.hasPrice() && !ctx.hasContact()) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "情感话术\"" + emotionWord + "\"且无价格/联系方式，缺乏实质信息"));
        }

        return Optional.empty();
    }
}
