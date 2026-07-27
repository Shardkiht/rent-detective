package io.github.shardkiht.rentdetective.semantic.pricing;

/**
 * 价格抽取结果。
 * 规则来自 104 条人工标注 + 规则清单第 1.5 节价格抽取规范。
 *
 * @param price             提取到的单一租金价格（多档报价时为 null）
 * @param multiTierPricing  是否为多档报价单（命中 price_menu_format 时为 true）
 * @param evidence          提取依据（原文片段），未提取到价格时为 null
 */
public record PriceExtraction(
        Double price,
        boolean multiTierPricing,
        String evidence
) {
    /** 便捷工厂：无价格、非多档报价 */
    public static PriceExtraction empty() {
        return new PriceExtraction(null, false, null);
    }

    /** 便捷工厂：多档报价 */
    public static PriceExtraction multiTier(String evidence) {
        return new PriceExtraction(null, true, evidence);
    }
}
