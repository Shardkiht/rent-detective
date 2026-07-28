package io.github.shardkiht.rentdetective.rules.pricing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 价格抽取器——从房源标题/描述中提取租金价格。
 * <p>
 * 规则来自 104 条人工标注 + 规则清单第 1.5 节价格抽取规范：
 * <ol>
 *   <li>只在明确的租金语境下提取：数字后面紧跟"元""元/月""租金""房租"等词</li>
 *   <li>距离/面积单位不算价格："2km""300米""700m²""56㎡"等不提取</li>
 *   <li>多档报价单不硬填单一值：命中 price_menu_format 时 price 留空，标记 multiTierPricing=true</li>
 *   <li>水电费、押金数字不算房租价格</li>
 * </ol>
 */
@Component
public class PriceExtractor {

    // ─── 多档报价检测（与 PriceMenuFormatMatcher 同源，cases: 17/56/87）───

    /** 多档报价：连续出现多个 "户型/房型 + 价格" 模式 */
    private static final Pattern MENU_PATTERN = Pattern.compile(
            "(单间|独卫|整租|一室|两室|三室|主卧|次卧|开间).{0,5}\\d{3,}.{0,10}" +
            "(单间|独卫|整租|一室|两室|三室|主卧|次卧|开间).{0,5}\\d{3,}");

    /** 备选：用 + 或逗号分隔的多档价格 */
    private static final Pattern MULTI_PRICE_PATTERN = Pattern.compile(
            "\\d{3,}元?.{0,5}[+＋、,，].{0,5}\\d{3,}元?.{0,5}[+＋、,，].{0,5}\\d{3,}");

    // ─── 非价格单位排除 ───

    /**
     * 水电费/押金语境：数字出现在这些关键词附近时不算房租。
     * 案例：id 2 "电费白天0.56度"、id 28 "水4.5元每吨，电0.8每度"
     */
    private static final Pattern UTILITY_CONTEXT = Pattern.compile(
            "(水费|电费|水[电]?[0-9]*[元块]|电费?[0-9]*[元块]|" +
            "水.*元.*[吨立]|电.*元.*度|气.*元.*[立方的]|" +
            "物业.*元|网费.*元|卫生.*元|垃圾.*元|" +
            "押[金付]|押金|定金|保证金)");

    // ─── 租金价格提取 ───

    /**
     * 租金语境价格提取：匹配 "数字 + 租金语境后缀"。
     * 支持格式：1000元、1000元/月、月租1000、租金1000、房租1000、1000/月 等。
     * 规则来自规则清单第 1.5 节第 1 条。
     */
    private static final Pattern RENT_PRICE_PATTERN = Pattern.compile(
            "(?:月租|租金|房租|月租金)[：:是为]?\\s*(\\d{3,})|(\\d{3,})\\s*[元块](?:\\s*/\\s*月)?|(\\d{3,})\\s*/\\s*月"
);

    /**
     * 宽泛数字提取（兜底）：当文本中有明确价格类字段位置时使用。
     * 只匹配 3-5 位数字（房租通常在 500-99999 范围）。
     */
    private static final Pattern BROAD_NUMBER_PATTERN = Pattern.compile(
            "(?:价格|租金|房租|月租)[：:是为]?\\s*(\\d{3,5})");

    /**
     * 从 description/title 中提取租金价格。
     * <p>
     * 规则（来自 spec 4.4 和规则清单第 1.5 节）：
     * <ol>
     *   <li>只在明确的租金语境下提取：数字后面紧跟"元""元/月""租金""房租"等词，
     *       或者数字本身处于价格类字段位置</li>
     *   <li>距离/面积单位不算价格："2km""300米""700m²""56㎡"这类单位后面的数字不提取。
     *       真实误判案例：id 51 描述里"2km内天街银泰"曾被误抓成 2000 元</li>
     *   <li>多档报价单不硬填单一值：命中 price_menu_format 时 price 留空，
     *       改标记 multiTierPricing=true</li>
     *   <li>水电费、押金数字不算房租价格："水费20元/人/月""电费1元/度""押一付一"中的数字不提取</li>
     * </ol>
     *
     * @param title       房源标题
     * @param description 房源描述
     * @return PriceExtraction 结果
     */
    public PriceExtraction extract(String title, String description) {
        String titleStr = title != null ? title : "";
        String descStr = description != null ? description : "";
        String text = titleStr + " " + descStr;

        // ── 第 0 步：检测多档报价（cases: 17/56/87）──
        if (isMultiTierPricing(text)) {
            return PriceExtraction.multiTier(extractMultiTierEvidence(text));
        }

        // ── 第 1 步：从租金语境中提取价格 ──
        Double price = extractRentPrice(text);

        if (price != null) {
            String evidence = findEvidenceSnippet(text, price);
            return new PriceExtraction(price, false, evidence);
        }

        // ── 第 2 步：兜底——价格类字段位置 ──
        price = extractFromPriceField(text);
        if (price != null) {
            String evidence = findEvidenceSnippet(text, price);
            return new PriceExtraction(price, false, evidence);
        }

        return PriceExtraction.empty();
    }

    /**
     * 检测是否为多档报价单。
     * 规则来自规则清单第 1.5 节第 3 条 + price_menu_format 规则（cases: 17/56/87）。
     */
    private boolean isMultiTierPricing(String text) {
        return MENU_PATTERN.matcher(text).find()
                || MULTI_PRICE_PATTERN.matcher(text).find();
    }

    /**
     * 提取多档报价的证据片段。
     */
    private String extractMultiTierEvidence(String text) {
        Matcher m = MENU_PATTERN.matcher(text);
        if (m.find()) {
            int start = Math.max(0, m.start() - 5);
            int end = Math.min(text.length(), m.end() + 5);
            return text.substring(start, end).trim();
        }
        m = MULTI_PRICE_PATTERN.matcher(text);
        if (m.find()) {
            return m.group().trim();
        }
        return "多档报价";
    }

    /**
     * 从租金语境中提取价格。
     * 核心逻辑：先排除非价格单位（距离/面积）和水电费语境，再从合法租金语境提取。
     */
    private Double extractRentPrice(String text) {
        // 收集所有候选价格
        List<CandidatePrice> candidates = new ArrayList<>();

        Matcher m = RENT_PRICE_PATTERN.matcher(text);
        while (m.find()) {
            String numStr = m.group(1) != null ? m.group(1)
                    : m.group(2) != null ? m.group(2)
                    : m.group(3);
            if (numStr != null) {
                try {
                    double val = Double.parseDouble(numStr);
                    candidates.add(new CandidatePrice(val, m.start(), m.end(), m.group()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // 过滤：排除非价格单位和水电费语境
        for (CandidatePrice c : candidates) {
            if (isInUtilityContext(text, c.start, c.end)) {
                continue;
            }
            if (isAdjacentToNonPriceUnit(text, c.end)) {
                continue;
            }
            // 合法租金价格
            return c.value;
        }

        return null;
    }

    /**
     * 兜底：从价格类字段位置提取数字。
     * 规则来自规则清单第 1.5 节第 1 条："数字本身处于价格类字段位置"。
     */
    private Double extractFromPriceField(String text) {
        Matcher m = BROAD_NUMBER_PATTERN.matcher(text);
        while (m.find()) {
            String numStr = m.group(1);
            try {
                double val = Double.parseDouble(numStr);
                if (!isInUtilityContext(text, m.start(), m.end())) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * 检查候选价格是否处于水电费/押金语境。
     * 规则来自规则清单第 1.5 节第 4 条。
     * 只检查候选数字前方 20 字——水电费关键词通常在数字前面（如"电费1元"），
     * 而租金价格后面的水电费描述（如"月租1000，电费0.5元"）不应反向污染。
     */
    private boolean isInUtilityContext(String text, int start, int end) {
        int ctxStart = Math.max(0, start - 20);
        String beforeContext = text.substring(ctxStart, end);
        return UTILITY_CONTEXT.matcher(beforeContext).find();
    }

    private static final Pattern NON_PRICE_UNIT_PATTERN = Pattern.compile(
            "^\\s*(km|KM|米|m²|㎡|平方|平米|平方米).*"
    );

    /**
     * 检查候选数字是否与距离/面积单位相邻。
     * 规则来自规则清单第 1.5 节第 2 条。
     * 真实误判案例：id 51 "2km内天街银泰" 被误提取为 2000 元。
     */
    private boolean isAdjacentToNonPriceUnit(String text, int end) {
        // 检查候选数字后面是否紧跟距离/面积单位
        String after = text.substring(end, Math.min(text.length(), end + 10));
        return NON_PRICE_UNIT_PATTERN.matcher(after).matches();
    }

    /**
     * 找到价格数字在原文中的证据片段（前后各取 20 字）。
     */
    private String findEvidenceSnippet(String text, double price) {
        String priceStr = String.valueOf((int) price);
        int idx = text.indexOf(priceStr);
        if (idx < 0) {
            // 尝试不带小数点
            priceStr = String.valueOf(price);
            idx = text.indexOf(priceStr);
        }
        if (idx >= 0) {
            int start = Math.max(0, idx - 15);
            int end = Math.min(text.length(), idx + priceStr.length() + 15);
            return text.substring(start, end).trim();
        }
        return "租金 " + (int) price + " 元";
    }

    /** 候选价格内部记录 */
    private record CandidatePrice(double value, int start, int end, String matched) {}
}
