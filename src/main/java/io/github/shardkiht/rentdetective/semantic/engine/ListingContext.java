package io.github.shardkiht.rentdetective.semantic.engine;

import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.semantic.pricing.PriceExtraction;
import io.github.shardkiht.rentdetective.semantic.pricing.PriceExtractor;

import java.util.regex.Pattern;

/**
 * 房源上下文，封装从 Listing 解析出的结构化信息。
 * description 头部解析格式：昵称
时间
已编辑?
属地
正文。
 */
public record ListingContext(
        Integer listingId,
        String title,
        String description,
        String body,
        String nickname,
        String ipRegion,
        boolean hasPrice,
        boolean hasContact,
        boolean hasLocation,
        String phone,
        Double price,
        String location,
        PriceExtraction priceExtraction
) {

    private static final Pattern PRICE_PATTERN = Pattern.compile("\\d+[元块/月]|价格|租金|\\d{3,}元");
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern WECHAT_PATTERN = Pattern.compile("微信[:：]");
    private static final Pattern DATE_LINE_PATTERN = Pattern.compile(".*20\\d{2}[-/].*");

    /**
     * 从 Listing 构建 ListingContext。
     * 关键：description 头部解析——格式为 昵称
时间
已编辑?
属地
正文。
     * 如果首行看起来像日期（含"2026-"等）或超过20字，视为无昵称（搜索快照格式）。
     * 昵称超过 20 字视为无昵称（规避陷阱 #1）。
     */
    public static ListingContext fromListing(Listing listing, PriceExtractor priceExtractor) {
        String desc = listing.getDescription() != null ? listing.getDescription() : "";
        String title = listing.getTitle() != null ? listing.getTitle() : "";

        // 解析头部
        String nickname = null;
        String ipRegion = null;
        String body = desc;

        if (!desc.isEmpty()) {
            String[] lines = desc.split("\n", -1);
            int bodyStart = 0;

            if (lines.length >= 1) {
                String firstLine = lines[0].trim();
                // 首行像日期或超过20字 → 搜索快照格式，无昵称
                if (!isDateLike(firstLine) && firstLine.length() <= 20 && !firstLine.isEmpty()) {
                    nickname = firstLine;
                    bodyStart = 1;
                }
            }

            // 尝试从后续行提取 IP 属地（通常格式为 "IP属地：XX" 或单独一行属地信息）
            for (int i = bodyStart; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.contains("IP属地") || line.matches(".*[省市自治区]$")) {
                    ipRegion = line.replace("IP属地", "").replace("：", "").replace(":", "").trim();
                    if (bodyStart <= i) {
                        bodyStart = i + 1;
                    }
                    break;
                }
            }

            // 昵称超过 20 字视为无昵称
            if (nickname != null && nickname.length() > 20) {
                nickname = null;
            }

            // body 为剩余行拼接
            StringBuilder sb = new StringBuilder();
            for (int i = bodyStart; i < lines.length; i++) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(lines[i]);
            }
            body = sb.toString();
        }

        // 检测 hasPrice / hasContact / hasLocation（基于 title + body）
        String titleAndBody = title + " " + body;
        boolean hasPrice = PRICE_PATTERN.matcher(titleAndBody).find();
        boolean hasContact = PHONE_PATTERN.matcher(titleAndBody).find()
                || WECHAT_PATTERN.matcher(titleAndBody).find();
        boolean hasLocation = listing.getLocation() != null && !listing.getLocation().isBlank();

        // 价格抽取（规则来自 104 条人工标注 + 规则清单第 1.5 节，先于规则打分执行）
        PriceExtraction priceExtraction = priceExtractor.extract(title, desc);

        return new ListingContext(
                listing.getId() != null ? listing.getId().intValue() : null,
                title,
                desc,
                body,
                nickname,
                ipRegion,
                hasPrice,
                hasContact,
                hasLocation,
                listing.getPhone(),
                listing.getPrice(),
                listing.getLocation(),
                priceExtraction
        );
    }

    private static boolean isDateLike(String line) {
        if (line.isEmpty()) return true;
        return DATE_LINE_PATTERN.matcher(line).matches()
                || line.matches("\\d{4}[-/]\\d{1,2}.*")
                || line.contains("发布于") || line.contains("编辑于");
    }
}
