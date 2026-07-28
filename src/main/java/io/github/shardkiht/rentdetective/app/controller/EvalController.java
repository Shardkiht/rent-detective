package io.github.shardkiht.rentdetective.app.controller;

import io.github.shardkiht.rentdetective.app.eval.ComparisonEvalService;
import io.github.shardkiht.rentdetective.app.eval.ComparisonReport;
import io.github.shardkiht.rentdetective.app.service.EvalService;
import io.github.shardkiht.rentdetective.rag.CaseVectorService;
import io.github.shardkiht.rentdetective.semantic.eval.EvalReport;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;
    private final ComparisonEvalService comparisonEvalService;
    private final CaseVectorService caseVectorService;

    public EvalController(EvalService evalService, ComparisonEvalService comparisonEvalService, CaseVectorService caseVectorService) {
        this.evalService = evalService;
        this.comparisonEvalService = comparisonEvalService;
        this.caseVectorService = caseVectorService;
    }

    /** 重新嵌入向量（切换 embedding 模型后调用） */
    @PostMapping("/reembed")
    public Map<String, String> reembed() {
        caseVectorService.reembedAll();
        return Map.of("message", "向量已重新嵌入");
    }

    /** RAG 搜索调试：查询文本返回 Top-K 相似案例 */
    @GetMapping("/search-debug")
    public List<io.github.shardkiht.rentdetective.rag.SimilarCase> searchDebug(
            @RequestParam String text,
            @RequestParam(defaultValue = "5") int k,
            @RequestParam(required = false) String excludeIds) {
        Set<Integer> exclude = null;
        if (excludeIds != null && !excludeIds.isBlank()) {
            exclude = Set.of(excludeIds.split(","))
                    .stream().map(String::trim).map(Integer::parseInt)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return caseVectorService.search(text, k, null, exclude);
    }

    /** 原有纯规则引擎评测（从 CSV），可选传入 csvPath 指定评测集 */
    @GetMapping("/rule-csv")
    public List<EvalReport> evalFromCsv(
            @RequestParam(required = false) String csvPath) throws Exception {
        return evalService.runEval(csvPath);
    }

    /**
     * 三方案对比评测（同步，适合 rule 策略）。
     * GET /api/eval/compare?strategy=rule|llm|agent
     * 可选参数 listingIds: 逗号分隔的 ID 列表，仅评测指定条目
     */
    @GetMapping("/compare")
    public ComparisonReport compare(
            @RequestParam(defaultValue = "rule") String strategy,
            @RequestParam(required = false) String listingIds) {
        if (listingIds != null && !listingIds.isBlank()) {
            List<Long> ids = Stream.of(listingIds.split(",")).map(String::trim).map(Long::parseLong).toList();
            return comparisonEvalService.run(strategy, ids);
        }
        return comparisonEvalService.run(strategy);
    }

    /**
     * 异步启动评测（立即返回，后台执行，适合 llm/agent）。
     * POST /api/eval/start?strategy=llm&listingIds=1,2,3
     */
    @PostMapping("/start")
    public Map<String, String> start(@RequestParam(defaultValue = "llm") String strategy,
                                     @RequestParam(required = false) String listingIds) {
        if (listingIds != null && !listingIds.isBlank()) {
            List<Long> ids = Stream.of(listingIds.split(",")).map(String::trim).map(Long::parseLong).toList();
            comparisonEvalService.startAsync(strategy, ids);
        } else {
            comparisonEvalService.startAsync(strategy);
        }
        return Map.of("message", "评测已启动", "strategy", strategy,
                "statusUrl", "/api/eval/progress?strategy=" + strategy);
    }

    /**
     * 查询评测进度/结果。
     * GET /api/eval/progress?strategy=llm
     */
    @GetMapping("/progress")
    public ComparisonEvalService.EvalProgress progress(@RequestParam(defaultValue = "llm") String strategy) {
        return comparisonEvalService.getProgress(strategy);
    }

    /**
     * 一键跑全部三种策略（耗时较长，agent 策略约 30 分钟）。
     * GET /api/eval/compare-all
     */
    @GetMapping("/compare-all")
    public Map<String, ComparisonReport> compareAll() {
        Map<String, ComparisonReport> results = new java.util.LinkedHashMap<>();
        results.put("rule", comparisonEvalService.run("rule"));
        results.put("llm", comparisonEvalService.run("llm"));
        results.put("agent", comparisonEvalService.run("agent"));
        return results;
    }
}
