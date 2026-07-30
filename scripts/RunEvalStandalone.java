
import io.github.shardkiht.rentdetective.domain.entity.Listing;
import io.github.shardkiht.rentdetective.eval.judge.JudgeUtils;
import io.github.shardkiht.rentdetective.eval.runner.EvalReport;
import io.github.shardkiht.rentdetective.rules.engine.*;
import io.github.shardkiht.rentdetective.rules.matcher.*;
import io.github.shardkiht.rentdetective.rules.pricing.PriceExtractor;
import io.github.shardkiht.rentdetective.rules.relation.*;
import io.github.shardkiht.rentdetective.rules.*;
import java.lang.reflect.Field;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 独立运行器：无需 Spring Boot / MySQL / Redis，直接跑规则引擎评测。
 * 
 * 用法: java -cp target/classes:$(cat /tmp/classpath.txt) scripts/RunEvalStandalone.java [评测集CSV] [全量CSV]
 * 
 * 默认: 评测集_24条.csv + 杭州租房_104条_评测终版.csv
 */
public class RunEvalStandalone {

    public static void main(String[] args) throws Exception {
        String evalCsvPath = args.length > 0 ? args[0] : "eval/评测集_24条.csv";
        String fullCsvPath = args.length > 1 ? args[1] : "eval/杭州租房_104条_评测终版.csv";

        System.out.println("=== RentDetective 规则引擎独立评测 ===");
        System.out.println("评测集: " + evalCsvPath);
        System.out.println("全量数据(关系规则用): " + fullCsvPath);

        // ── 1. 创建 In-Memory RelationListingMapper ──
        InMemoryRelationMapper relationMapper = new InMemoryRelationMapper();
        relationMapper.loadFromCsv(resolvePath(fullCsvPath));

        // ── 2. 创建 ScamRuleRegistry（手动初始化） ──
        ScamRuleRegistry registry = new ScamRuleRegistry();
        registry.init();

        // ── 3. 创建所有 RuleMatcher ──
        List<RuleMatcher> matchers = List.of(
                new OverDenialMatcher(),
                new AgentStockPhraseMatcher(),
                new PriceMenuFormatMatcher(),
                new CoverageLanguageMatcher(),
                new SalesOverSubstanceMatcher(),
                new SelfDisclosedAgentMatcher(),
                new ContactOnlyBodyMatcher(),
                new WechatOnlyWeakMatcher(),
                new EmotionalNarrativeMatcher(),
                new IdentityMixedMatcher(),
                new PersonaMismatchMatcher(),
                new PhoneObfuscationMatcher(),
                new ContactSpamMatcher(),
                new OutOfRegionIpMatcher(),
                new VerifiableEndorsementMatcher(),
                new UnverifiableEndorsementMatcher()
        );

        // ── 4. 创建各组件 ──
        PriceExtractor priceExtractor = new PriceExtractor();
        AdviceGenerator adviceGenerator = new AdviceGenerator();
        RelationRuleService relationService = new RelationRuleService(relationMapper);

        // ── 5. 创建 RuleEngine 并设置阈值 ──
        RuleEngine ruleEngine = new RuleEngine(registry, matchers, adviceGenerator, relationService);
        setField(ruleEngine, "suspiciousThreshold", 0.6);
        setField(ruleEngine, "reviewThreshold", 0.4);

        // ── 6. 读取评测集 CSV ──
        List<CSVRow> rows = readCsv(resolvePath(evalCsvPath));
        System.out.println("\n读取评测集: " + rows.size() + " 条");

        // ── 7. 运行规则引擎评测 ──
        Map<String, List<EvalReport.MisCase>> misCasesByGroup = new LinkedHashMap<>();
        Map<String, int[]> statsByGroup = new LinkedHashMap<>(); // [correct, total]
        for (String g : List.of("normal", "insufficient", "not_listing")) {
            misCasesByGroup.put(g, new ArrayList<>());
            statsByGroup.put(g, new int[]{0, 0});
        }

        int correct = 0;
        int total = 0;

        for (CSVRow row : rows) {
            int id = row.getInt("id");
            String title = row.get("title");
            String description = row.get("description");
            String priceStr = row.get("price");
            String location = row.get("location");
            String phone = row.get("phone");
            String riskLevel = row.get("risk_level");
            String evalGroup = row.get("eval_group");
            if (evalGroup == null || evalGroup.isBlank()) {
                evalGroup = "normal";
            }

            // 构建 Listing
            Listing listing = new Listing();
            listing.setId((long) id);
            listing.setTitle(title);
            listing.setDescription(description);
            listing.setLocation(location);
            listing.setPhone(phone);
            if (priceStr != null && !priceStr.isBlank()) {
                try {
                    listing.setPrice(Double.parseDouble(priceStr));
                } catch (NumberFormatException ignored) {}
            }

            // 运行规则引擎
            ListingContext ctx = ListingContext.fromListing(listing, priceExtractor);
            EngineResult result = ruleEngine.evaluate(ctx);
            String predicted = result.verdict().name();

            // 统计
            total++;
            boolean isCorrect = JudgeUtils.judgeCorrect(evalGroup, riskLevel, predicted);
            if (isCorrect) correct++;

            int[] stats = statsByGroup.get(evalGroup);
            if (stats != null) {
                stats[1]++; // total
                if (isCorrect) stats[0]++; // correct
            }

            if (!isCorrect) {
                String hitRules = result.hits().stream()
                        .map(RuleHit::ruleType)
                        .collect(java.util.stream.Collectors.joining(","));
                misCasesByGroup.computeIfAbsent(evalGroup, k -> new ArrayList<>())
                        .add(new EvalReport.MisCase(id, riskLevel, predicted, hitRules));
            }
        }

        // ── 8. 输出结果 ──
        System.out.println("\n=== 评测结果（规则引擎 / " + evalCsvPath + "） ===");
        for (Map.Entry<String, int[]> entry : statsByGroup.entrySet()) {
            String group = entry.getKey();
            int[] stats = entry.getValue();
            double accuracy = stats[1] > 0 ? (double) stats[0] / stats[1] * 100 : 0;
            System.out.printf("\n--- %s 组 ---\n", group);
            System.out.printf("  总数: %d, 正确: %d, 准确率: %.1f%%\n", stats[1], stats[0], accuracy);

            List<EvalReport.MisCase> misCases = misCasesByGroup.get(group);
            if (!misCases.isEmpty()) {
                System.out.println("  错分案例:");
                for (EvalReport.MisCase mis : misCases) {
                    System.out.printf("    ID=%d, 人工=%s, 预测=%s, 命中规则=[%s]\n",
                            mis.id(), mis.humanLabel(), mis.predicted(), mis.hitRules());
                }
            }
        }

        double overallAccuracy = total > 0 ? (double) correct / total * 100 : 0;
        System.out.printf("\n=== 总体准确率: %.1f%% (%d/%d) ===\n", overallAccuracy, correct, total);
    }

    // ── 辅助方法 ──

    private static Path resolvePath(String csvPath) {
        // 先在 src/main/resources 下找
        Path p = Path.of("src/main/resources", csvPath);
        if (Files.exists(p)) return p;
        // 再直接找
        p = Path.of(csvPath);
        if (Files.exists(p)) return p;
        // 再在 target/classes 下找
        p = Path.of("target/classes", csvPath);
        if (Files.exists(p)) return p;
        return Path.of(csvPath); // 让调用方报 IOException
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ── CSV 解析 ──

    private record CSVRow(Map<String, String> fields) {
        String get(String key) { return fields.getOrDefault(key, ""); }
        int getInt(String key) {
            try { return Integer.parseInt(get(key).trim()); }
            catch (NumberFormatException e) { return 0; }
        }
    }

    private static List<CSVRow> readCsv(Path path) throws IOException {
        List<CSVRow> rows = new ArrayList<>();
        try (InputStream is = new FileInputStream(path.toFile());
             PushbackInputStream pis = new PushbackInputStream(is, 3)) {
            // 跳过 BOM
            int b1 = pis.read();
            if (b1 == 0xEF) {
                int b2 = pis.read();
                int b3 = pis.read();
                if (b2 != 0xBB || b3 != 0xBF) {
                    pis.unread(b3);
                    pis.unread(b2);
                }
            } else if (b1 != -1) {
                pis.unread(b1);
            }

            try (InputStreamReader reader = new InputStreamReader(pis, StandardCharsets.UTF_8);
                 org.apache.commons.csv.CSVParser parser = org.apache.commons.csv.CSVParser.parse(reader,
                         org.apache.commons.csv.CSVFormat.DEFAULT.builder()
                                 .setHeader()
                                 .setSkipHeaderRecord(true)
                                 .setIgnoreHeaderCase(true)
                                 .setTrim(true)
                                 .build())) {
                for (org.apache.commons.csv.CSVRecord record : parser) {
                    Map<String, String> fields = new HashMap<>();
                    for (String header : parser.getHeaderNames()) {
                        fields.put(header, record.get(header));
                    }
                    rows.add(new CSVRow(fields));
                }
            }
        }
        return rows;
    }

    // ── In-Memory Relation Listing Mapper ──

    static class InMemoryRelationMapper implements RelationListingMapper {
        private final Map<String, List<String>> phoneTitles = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> phoneRecords = new HashMap<>();

        void loadFromCsv(Path path) {
            try {
                List<CSVRow> rows = readCsv(path);
                for (CSVRow row : rows) {
                    String phone = row.get("phone").trim();
                    String title = row.get("title").trim();
                    String priceStr = row.get("price").trim();

                    if (phone.isEmpty() || title.isEmpty()) continue;

                    phoneTitles.computeIfAbsent(phone, k -> new ArrayList<>()).add(title);

                    Map<String, Object> record = new HashMap<>();
                    record.put("id", row.get("id"));
                    record.put("title", title);
                    try {
                        record.put("price", priceStr.isEmpty() ? null : Double.parseDouble(priceStr));
                    } catch (NumberFormatException e) {
                        record.put("price", null);
                    }
                    phoneRecords.computeIfAbsent(phone, k -> new ArrayList<>()).add(record);
                }
                System.out.println("已加载关系规则数据: " + phoneTitles.size() + " 个不同联系方式");
            } catch (Exception e) {
                System.err.println("加载全量CSV失败: " + e.getMessage());
            }
        }

        @Override
        public int countDistinctTitlesByPhone(String phone) {
            List<String> titles = phoneTitles.get(phone);
            if (titles == null) return 0;
            return (int) titles.stream().distinct().count();
        }

        @Override
        public List<Map<String, Object>> selectByPhoneWithPrice(String phone) {
            return phoneRecords.getOrDefault(phone, List.of());
        }
    }
}