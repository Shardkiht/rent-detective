package io.github.shardkiht.rentdetective.rules.engine;

import io.github.shardkiht.rentdetective.domain.entity.ScamRule;
import io.github.shardkiht.rentdetective.rules.relation.RelationRuleService;
import io.github.shardkiht.rentdetective.rules.ScamRuleRegistry;
import io.github.shardkiht.rentdetective.rules.matcher.RuleHit;
import io.github.shardkiht.rentdetective.rules.matcher.RuleMatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 规则引擎。规则来自 104 条人工标注，规则引擎为确定性打分。
 * 三步顺序：not_listing → info_insufficient → 加权打分。
 */
@Service
public class RuleEngine {

    @Value("${rule.threshold.suspicious}")
    private double suspiciousThreshold;

    @Value("${rule.threshold.review}")
    private double reviewThreshold;

    private final ScamRuleRegistry registry;
    private final List<RuleMatcher> matchers;
    private final AdviceGenerator adviceGenerator;
    private final RelationRuleService relationRuleService;

    /** 非房源标题关键词 */
    private static final Pattern NOT_LISTING_PATTERN = Pattern.compile("求租|找室友|值得嘛");
    private static final Pattern HAS_ROOM_PATTERN = Pattern.compile("有房");

    /** info_insufficient 闸门常量 —— 与 eval_group 共用同一份定义 */
    private static final int BODY_MIN_LENGTH = 5;
    private static final int BODY_SHORT_THRESHOLD = 20;
    private static final int BODY_MEDIUM_THRESHOLD = 60;
    private static final int CORE_MISSING_THRESHOLD = 2;

    public RuleEngine(ScamRuleRegistry registry,
                      List<RuleMatcher> matchers,
                      AdviceGenerator adviceGenerator,
                      RelationRuleService relationRuleService) {
        this.registry = registry;
        this.matchers = matchers;
        this.adviceGenerator = adviceGenerator;
        this.relationRuleService = relationRuleService;
    }

    /**
     * 评估房源上下文，返回引擎结果。
     * 三步顺序不可改变：not_listing → info_insufficient → 加权打分。
     */
    public EngineResult evaluate(ListingContext ctx) {
        // === 第一步：not_listing 判断 ===
        String title = ctx.title() != null ? ctx.title() : "";
        if (NOT_LISTING_PATTERN.matcher(title).find() && !HAS_ROOM_PATTERN.matcher(title).find()) {
            return new EngineResult(Verdict.NOT_LISTING, 0.0, List.of(), List.of(),
                    "标题含非房源关键词且不含\"有房\"");
        }

        // === 第二步：info_insufficient 闸门 ===
        String body = ctx.body() != null ? ctx.body() : "";
        Set<String> missingItems = detectMissingItems(ctx);

        if (isInfoInsufficient(body, ctx, missingItems)) {
            List<String> advice = adviceGenerator.generate(missingItems, List.of());
            String reason = buildInsufficientReason(body, missingItems);
            return new EngineResult(Verdict.INSUFFICIENT, 0.0, List.of(), advice, reason);
        }

        // === 第三步：加权打分 ===
        // 只运行启用的 matcher
        Set<String> enabledTypes = registry.getEnabledRules().stream()
                .map(ScamRule::getRuleType)
                .collect(Collectors.toSet());

        List<RuleHit> hits = new ArrayList<>();
        for (RuleMatcher matcher : matchers) {
            if (enabledTypes.contains(matcher.ruleType())) {
                matcher.match(ctx).ifPresent(hits::add);
            }
        }

        // 关系规则（陷阱 #4：只在第三步打分环节生效，第二步分流不拦截带联系方式的短正文条目）
        if (ctx.phone() != null && !ctx.phone().isBlank()) {
            relationRuleService.checkPhoneCluster(ctx.phone()).ifPresent(hits::add);
            relationRuleService.checkSamePhoneDifferentPrice(ctx.phone(), ctx.title(), ctx.price()).ifPresent(hits::add);
        }

        double score = hits.stream().mapToDouble(RuleHit::weight).sum();
        long strongNeg = hits.stream().filter(h -> h.weight() >= 0.6).count();

        Verdict verdict;
        if (score >= suspiciousThreshold) {
            // 含 verifiable_endorsement 且 strongNeg >= 2 → REVIEW
            boolean hasVerifiable = hits.stream().anyMatch(h -> "verifiable_endorsement".equals(h.ruleType()));
            if (hasVerifiable && strongNeg >= 2) {
                verdict = Verdict.REVIEW;
            } else {
                verdict = Verdict.SUSPICIOUS;
            }
        } else if (score >= reviewThreshold) {
            verdict = Verdict.REVIEW;
        } else {
            verdict = Verdict.SAFE;
        }

        List<String> advice = adviceGenerator.generate(missingItems, hits);
        String hitRules = hits.stream().map(RuleHit::ruleType).collect(Collectors.joining(", "));
        String reason = hits.isEmpty() ? "无命中规则" : "命中规则: " + hitRules;

        return new EngineResult(verdict, score, hits, advice, reason);
    }

    private static final Pattern PHONE_IN_TEXT_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    /** 微信号/联系方式指示词（正文或 phone 字段中含这些词表示有联系渠道） */
    private static final Pattern WECHAT_INDICATOR_PATTERN = Pattern.compile("微信|➕|加v|联系方式|微信号");
    /** 昵称中的中介/职业房东关键词（与 Python 基线 gate_info_insufficient 同源） */
    private static final Pattern NICKNAME_AGENT_PATTERN = Pattern.compile("租房|中介|公寓|房产|置业|民宿|短租");

    /**
     * info_insufficient 闸门条件。
     * 条件与 eval_group 生成条件共用同一份定义（抽常量禁止复制粘贴）。
     */
    private boolean isInfoInsufficient(String body, ListingContext ctx, Set<String> missingItems) {
        return body.length() < BODY_MIN_LENGTH
                || body.endsWith("...") || body.endsWith("…")
                || (body.length() < BODY_SHORT_THRESHOLD && !ctx.hasContact())
                || (missingItems.size() >= CORE_MISSING_THRESHOLD && body.length() < BODY_MEDIUM_THRESHOLD)
                // 短正文 + 昵称自曝中介 + 无价格（与 Python 基线 gate_info 同源）
                || (body.length() < BODY_SHORT_THRESHOLD && hasAgentNickname(ctx) && !ctx.hasPrice())
                // 中等正文 + 无任何联系渠道（无手机号且无微信指示），阈值与 Python 基线同源
                || (body.length() < BODY_SHORT_THRESHOLD + 25 && !ctx.hasContact() && !hasPhoneAnywhere(ctx) && !hasWechatIndicator(ctx));
    }

    /** 检查昵称是否含中介/职业房东关键词 */
    private boolean hasAgentNickname(ListingContext ctx) {
        String nickname = ctx.nickname();
        return nickname != null && !nickname.isBlank() && NICKNAME_AGENT_PATTERN.matcher(nickname).find();
    }

    /** 检查是否有手机号（结构化字段或正文中） */
    private boolean hasPhoneAnywhere(ListingContext ctx) {
        String phone = ctx.phone();
        if (phone != null && !phone.isBlank() && PHONE_IN_TEXT_PATTERN.matcher(phone).find()) {
            return true;
        }
        String body = ctx.body() != null ? ctx.body() : "";
        return PHONE_IN_TEXT_PATTERN.matcher(body).find();
    }

    /** 检查是否有微信/联系方式指示（phone 字段或正文中含微信相关词） */
    private boolean hasWechatIndicator(ListingContext ctx) {
        String phone = ctx.phone();
        if (phone != null && !phone.isBlank() && WECHAT_INDICATOR_PATTERN.matcher(phone).find()) {
            return true;
        }
        String body = ctx.body() != null ? ctx.body() : "";
        return WECHAT_INDICATOR_PATTERN.matcher(body).find();
    }

    /**
     * 检测核心信息缺失项。
     * 核心信息 = 价格/联系方式/位置，从 title+body 检测。
     */
    private Set<String> detectMissingItems(ListingContext ctx) {
        Set<String> missing = new LinkedHashSet<>();
        if (!ctx.hasPrice()) missing.add("price");
        if (!ctx.hasContact()) missing.add("contact");
        if (!ctx.hasLocation()) missing.add("location");

        String body = ctx.body() != null ? ctx.body() : "";
        if (body.length() < BODY_MIN_LENGTH) {
            missing.add("body");
        }
        return missing;
    }

    private String buildInsufficientReason(String body, Set<String> missingItems) {
        List<String> reasons = new ArrayList<>();
        if (body.length() < BODY_MIN_LENGTH) {
            reasons.add("正文过短（<5字）");
        } else if (body.endsWith("...") || body.endsWith("…")) {
            reasons.add("正文截断（以.../…结尾）");
        }
        if (missingItems.contains("price")) reasons.add("缺少价格信息");
        if (missingItems.contains("contact")) reasons.add("缺少联系方式");
        if (missingItems.contains("location")) reasons.add("缺少位置信息");
        if (missingItems.contains("body")) reasons.add("正文不足");
        return "信息不足: " + String.join("；", reasons);
    }

}
