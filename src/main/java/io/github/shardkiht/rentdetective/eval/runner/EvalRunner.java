package io.github.shardkiht.rentdetective.eval.runner;

import io.github.shardkiht.rentdetective.eval.judge.JudgeUtils;
import io.github.shardkiht.rentdetective.domain.entity.Listing;
import io.github.shardkiht.rentdetective.rules.engine.EngineResult;
import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import io.github.shardkiht.rentdetective.rules.engine.RuleEngine;
import io.github.shardkiht.rentdetective.rules.pricing.PriceExtractor;
import io.github.shardkiht.rentdetective.rules.engine.RuleHit;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评估运行器。读取 CSV 标注数据，逐条运行规则引擎，按分组计算准确率。
 * 规则来自 104 条人工标注，规则引擎为确定性打分。
 */
@Component
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    private static final String DEFAULT_CSV_PATH = "eval/杭州租房_104条_评测终版.csv";

    private final RuleEngine ruleEngine;
    private final PriceExtractor priceExtractor;

    public EvalRunner(RuleEngine ruleEngine, PriceExtractor priceExtractor) {
        this.ruleEngine = ruleEngine;
        this.priceExtractor = priceExtractor;
    }

    /**
     * 运行评估。
     *
     * @param csvPath CSV 文件路径（classpath 资源），为 null 时使用默认路径
     * @return 三组评估报告（normal / insufficient / not_listing）
     */
    public List<EvalReport> run(String csvPath) throws Exception {
        if (csvPath == null || csvPath.isBlank()) {
            csvPath = DEFAULT_CSV_PATH;
        }

        ClassPathResource resource = new ClassPathResource(csvPath);
        List<CSVRecord> records;
        try (InputStream rawIs = resource.getInputStream();
             PushbackInputStream is = new PushbackInputStream(rawIs, 3)) {
            // 处理 UTF-8 BOM：跳过开头的 EF BB BF
            int b1 = is.read();
            if (b1 == 0xEF) {
                int b2 = is.read();
                int b3 = is.read();
                if (b2 != 0xBB || b3 != 0xBF) {
                    // 不是 BOM，推回
                    is.unread(b3);
                    is.unread(b2);
                }
            } else if (b1 != -1) {
                is.unread(b1);
            }
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                 CSVParser parser = CSVFormat.DEFAULT.builder()
                         .setHeader()
                         .setSkipHeaderRecord(true)
                         .setIgnoreHeaderCase(true)
                         .setTrim(true)
                         .build()
                         .parse(reader)) {
                records = parser.getRecords();
            }
        }

        // 按 eval_group 分组收集结果
        List<RowResult> normalResults = new ArrayList<>();
        List<RowResult> insufficientResults = new ArrayList<>();
        List<RowResult> notListingResults = new ArrayList<>();

        for (CSVRecord record : records) {
            int id = parseIntSafe(record.get("id"));
            String title = getSafe(record, "title");
            String description = getSafe(record, "description");
            String priceStr = getSafe(record, "price");
            String location = getSafe(record, "location");
            String phone = getSafe(record, "phone");
            String riskLevel = getSafe(record, "risk_level");
            String evalGroup = getSafe(record, "eval_group");
            if (evalGroup.isBlank()) {
                // fallback: 从 risk_tags 推导
                String riskTags = getSafe(record, "risk_tags");
                evalGroup = computeEvalGroupFromRiskTags(riskTags);
                log.warn("eval_group 列缺失，使用 risk_tags 推导分组，id={}, 推导结果={}", id, evalGroup);
            }

            Double price = null;
            if (!priceStr.isBlank()) {
                try {
                    price = Double.parseDouble(priceStr);
                } catch (NumberFormatException ignored) {
                }
            }

            // 构建 Listing 对象
            Listing listing = new Listing();
            listing.setId((long) id);
            listing.setTitle(title);
            listing.setDescription(description);
            listing.setLocation(location);
            listing.setPhone(phone);
            listing.setPrice(price);

            ListingContext ctx = ListingContext.fromListing(listing, priceExtractor);
            EngineResult result = ruleEngine.evaluate(ctx);

            String hitRules = result.hits().stream()
                    .map(RuleHit::ruleType)
                    .collect(Collectors.joining(","));

            RowResult rowResult = new RowResult(id, riskLevel, result.verdict().name(), hitRules);

            switch (evalGroup) {
                case "insufficient" -> insufficientResults.add(rowResult);
                case "not_listing" -> notListingResults.add(rowResult);
                default -> normalResults.add(rowResult);
            }
        }

        // 计算各组准确率
        List<EvalReport> reports = new ArrayList<>();
        reports.add(buildReport("normal", normalResults));
        reports.add(buildReport("insufficient", insufficientResults));
        reports.add(buildReport("not_listing", notListingResults));
        return reports;
    }

    private EvalReport buildReport(String groupName, List<RowResult> results) {
        int total = results.size();
        int correct = 0;
        List<EvalReport.MisCase> misCases = new ArrayList<>();

        for (RowResult r : results) {
            boolean isCorrect = JudgeUtils.judgeCorrect(groupName, r.humanLabel, r.predicted);

            if (isCorrect) {
                correct++;
            } else {
                misCases.add(new EvalReport.MisCase(r.id, r.humanLabel, r.predicted, r.hitRules));
            }
        }

        double accuracy = total > 0 ? (double) correct / total : 0.0;
        return new EvalReport(groupName, total, correct, accuracy, misCases);
    }

    private static String getSafe(CSVRecord record, String column) {
        try {
            String val = record.get(column);
            return val != null ? val : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String computeEvalGroupFromRiskTags(String riskTags) {
        if (riskTags == null || riskTags.isBlank()) {
            return "normal";
        }
        String lower = riskTags.toLowerCase();
        if (lower.contains("not_listing")) {
            return "not_listing";
        }
        if (lower.contains("info_insufficient")) {
            return "insufficient";
        }
        return "normal";
    }

    private static int parseIntSafe(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record RowResult(int id, String humanLabel, String predicted, String hitRules) {
    }
}
