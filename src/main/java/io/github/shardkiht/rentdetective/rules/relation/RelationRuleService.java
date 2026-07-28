package io.github.shardkiht.rentdetective.rules.relation;

import io.github.shardkiht.rentdetective.rules.matcher.RuleHit;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 关系规则服务。联系方式聚类 + 同号不同价，基于 MySQL 查询。
 * 规则来自 104 条人工标注，关系类证据优先级高于文本类规则（证据清单第二节）。
 * <p>
 * 已知陷阱 #4：关系规则只在第三步打分环节才生效，第二步分流不得拦截带联系方式的短正文条目。
 */
@Service
public class RelationRuleService {

    private static final String RULE_TYPE_PHONE_CLUSTER = "phone_cluster";
    private static final String RULE_TYPE_SAME_PHONE_DIFF_PRICE = "same_phone_different_price";

    /** 标点符号正则：中英文标点及常见特殊符号 */
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile(
            "[\\p{Punct}、。！？；：“”‘’【】（）《》…～·\\s]"
    );

    /** 标题相似度比较的前缀长度 */
    private static final int TITLE_PREFIX_LENGTH = 12;

    private final RelationListingMapper relationListingMapper;

    public RelationRuleService(RelationListingMapper relationListingMapper) {
        this.relationListingMapper = relationListingMapper;
    }

    /**
     * 联系方式聚类检测（规则清单第二节-规则1，cases: 32/33/40/57/60, 37/38/54, 89/90）。
     * 查询同一 phone 关联了多少条不同房源（不同标题）。
     * 规则（来自 spec 4.3）：
     * - ≥3 条不同房源 → weight 0.8
     * - 2 条不同房源 → weight 0.5
     * - 1 条或无 → 不命中
     *
     * @return RuleHit 如果命中，否则 Optional.empty()
     */
    public Optional<RuleHit> checkPhoneCluster(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }

        int distinctCount = relationListingMapper.countDistinctTitlesByPhone(phone);

        if (distinctCount >= 3) {
            String evidence = String.format("联系方式 %s 关联 %d 条不同房源（≥3，马甲组特征）", phone, distinctCount);
            return Optional.of(new RuleHit(RULE_TYPE_PHONE_CLUSTER, 0.8, evidence));
        } else if (distinctCount == 2) {
            String evidence = String.format("联系方式 %s 关联 2 条不同房源（重复发帖特征）", phone);
            return Optional.of(new RuleHit(RULE_TYPE_PHONE_CLUSTER, 0.5, evidence));
        }
        return Optional.empty();
    }

    /**
     * 同号不同价检测（规则清单第二节-规则2，cases: 93/95）。
     * 同一 phone 分组内，标题相似的房源是否存在不同价格。
     * 全数据集最硬的证据。
     * <p>
     * 规则（来自 spec 4.3）：
     * - 同 phone 分组内存在 ≥2 个不同 price（忽略 NULL）
     * - 且标题相似（"去除标点后前缀12字相同"判断，不引入文本相似度库）
     * - 命中 → weight 0.9
     * <p>
     * 真实案例：微信 wjzdcs 发两条一字不差的"阳光单间"，一条标1000、一条标1100（id 93/95）
     *
     * @return RuleHit 如果命中，否则 Optional.empty()
     */
    public Optional<RuleHit> checkSamePhoneDifferentPrice(String phone, String title, Double price) {
        if (phone == null || phone.isBlank() || title == null || price == null) {
            return Optional.empty();
        }

        List<Map<String, Object>> siblings = relationListingMapper.selectByPhoneWithPrice(phone);
        if (siblings == null || siblings.isEmpty()) {
            return Optional.empty();
        }

        // 收集同 phone 下标题相似但价格不同的记录
        Set<Double> distinctPrices = new HashSet<>();
        List<String> similarTitles = new ArrayList<>();

        for (Map<String, Object> row : siblings) {
            String otherTitle = row.get("title") != null ? row.get("title").toString() : "";
            Object priceObj = row.get("price");
            if (priceObj == null) continue;

            double otherPrice;
            if (priceObj instanceof Number) {
                otherPrice = ((Number) priceObj).doubleValue();
            } else {
                try {
                    otherPrice = Double.parseDouble(priceObj.toString());
                } catch (NumberFormatException e) {
                    continue;
                }
            }

            // 标题相似且价格不同
            if (isTitleSimilar(title, otherTitle) && otherPrice != price) {
                distinctPrices.add(otherPrice);
                similarTitles.add(otherTitle);
            }
        }

        // 加上当前 listing 自身的价格，判断是否有 ≥2 个不同价格
        distinctPrices.add(price);

        if (distinctPrices.size() >= 2 && !similarTitles.isEmpty()) {
            String evidence = String.format(
                    "同号不同价：联系方式 %s 下标题相似的房源存在 %d 种不同价格（%s），疑似虚假引流",
                    phone, distinctPrices.size(), distinctPrices
            );
            return Optional.of(new RuleHit(RULE_TYPE_SAME_PHONE_DIFF_PRICE, 0.9, evidence));
        }

        return Optional.empty();
    }

    /**
     * 标题相似度判断（辅助方法）。
     * "去除标点后前缀12字相同"即可（spec 4.3）。
     */
    boolean isTitleSimilar(String title1, String title2) {
        if (title1 == null || title2 == null) return false;
        String n1 = normalizeTitle(title1);
        String n2 = normalizeTitle(title2);
        if (n1.isEmpty() || n2.isEmpty()) return false;

        int len = Math.min(TITLE_PREFIX_LENGTH, Math.min(n1.length(), n2.length()));
        return n1.substring(0, len).equals(n2.substring(0, len));
    }

    /**
     * 去除标点符号后的标题。
     */
    private String normalizeTitle(String title) {
        return PUNCTUATION_PATTERN.matcher(title).replaceAll("");
    }
}
