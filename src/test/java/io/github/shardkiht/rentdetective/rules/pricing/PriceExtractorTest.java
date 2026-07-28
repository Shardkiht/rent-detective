package io.github.shardkiht.rentdetective.rules.pricing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PriceExtractor 单测。
 * 测试用例来自 104 条人工标注数据集中的真实案例。
 */
class PriceExtractorTest {

    private PriceExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PriceExtractor();
    }

    @Test
    @DisplayName("正常价格提取：月租1000 → price=1000")
    void testNormalPrice() {
        // 案例 id 2：月租 1000，电费白天 0.56 度
        PriceExtraction result = extractor.extract("月租1000", "月租：1000，民用电");
        assertEquals(1000.0, result.price(), "应提取月租 1000");
        assertFalse(result.multiTierPricing());
        assertNotNull(result.evidence());
    }

    @Test
    @DisplayName("正常价格提取：租金1400每月 → price=1400")
    void testNormalPriceWithRentKeyword() {
        // 案例 id 28：租金 1400 每月，水 4.5 元每吨，电 0.8 每度
        PriceExtraction result = extractor.extract("", "租金 1400 每月，水 4.5 元每吨，电 0.8 每度");
        assertEquals(1400.0, result.price(), "应提取租金 1400");
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("【最关键】id 51：2km内天街银泰 → price=null（不被误提取为 2000）")
    void testDistanceNotPrice() {
        // 真实误判案例：id 51 描述里"2km内天街银泰"曾被误抓成 2000 元
        PriceExtraction result = extractor.extract("", "2km内天街银泰，步行可达");
        assertNull(result.price(), "2km 的距离数字绝对不能被提取为价格");
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("多档报价单：单间800+独卫1000+整租2500 → multiTierPricing=true, price=null")
    void testMultiTierPricing() {
        // 案例 id 56
        String desc = "单间800＋单间独卫1000＋单间独厨独卫1500＋整租一室一厅2500＋整租两室3000＋";
        PriceExtraction result = extractor.extract("", desc);
        assertTrue(result.multiTierPricing(), "应标记为多档报价");
        assertNull(result.price(), "多档报价时 price 应为 null");
        assertNotNull(result.evidence());
    }

    @Test
    @DisplayName("多档报价单变体：id 17 格式")
    void testMultiTierPricingVariant() {
        // 案例 id 17
        String desc = "单间，公寓，1500-3000...一室1500-3000...整租3000起";
        PriceExtraction result = extractor.extract("", desc);
        assertTrue(result.multiTierPricing(), "应标记为多档报价");
        assertNull(result.price(), "多档报价时 price 应为 null");
    }

    @Test
    @DisplayName("水电费不算价格：水费20元/吨，电费1元/度 → price=null")
    void testUtilityNotPrice() {
        PriceExtraction result = extractor.extract("", "水费20元/吨，电费1元/度");
        assertNull(result.price(), "水电费数字不应被提取为租金价格");
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("押金不算价格：押一付一 → price=null")
    void testDepositNotPrice() {
        PriceExtraction result = extractor.extract("", "押一付一，拎包入住");
        assertNull(result.price(), "押金相关数字不应被提取为租金价格");
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("面积不算价格：56㎡ → price=null")
    void testAreaNotPrice() {
        PriceExtraction result = extractor.extract("", "精装修56㎡，采光好");
        assertNull(result.price(), "面积数字不应被提取为租金价格");
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("面积不算价格：700m² → price=null")
    void testAreaM2NotPrice() {
        PriceExtraction result = extractor.extract("", "厂房700m²出租");
        assertNull(result.price(), "面积数字不应被提取为租金价格");
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("混合场景：正常价格 + 水电费 → 只提取租金")
    void testMixedScenario() {
        // id 2 完整场景
        String desc = "月租：1000，民用电，电费白天0.56度，晚上0.36度";
        PriceExtraction result = extractor.extract("", desc);
        assertEquals(1000.0, result.price(), "应只提取月租 1000");
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("混合场景：距离 + 价格 → 只提取价格")
    void testDistanceAndPrice() {
        String desc = "2km内天街银泰，月租2000元";
        PriceExtraction result = extractor.extract("", desc);
        assertEquals(2000.0, result.price(), "应提取月租 2000，忽略 2km");
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("空文本 → price=null")
    void testEmptyInput() {
        PriceExtraction result = extractor.extract("", "");
        assertNull(result.price());
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("null 输入 → price=null")
    void testNullInput() {
        PriceExtraction result = extractor.extract(null, null);
        assertNull(result.price());
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("距离单位 300米 → price=null")
    void testDistanceMetersNotPrice() {
        PriceExtraction result = extractor.extract("", "距地铁站300米");
        assertNull(result.price(), "距离数字不应被提取为租金价格");
        assertFalse(result.multiTierPricing());
    }

    @Test
    @DisplayName("价格字段位置提取：价格：1500 → price=1500")
    void testPriceFieldPosition() {
        PriceExtraction result = extractor.extract("", "价格：1500");
        assertEquals(1500.0, result.price());
        assertFalse(result.multiTierPricing());
    }
}
