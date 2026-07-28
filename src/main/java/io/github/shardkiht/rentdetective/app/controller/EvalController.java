package io.github.shardkiht.rentdetective.app.controller;

import io.github.shardkiht.rentdetective.app.eval.ComparisonEvalService;
import io.github.shardkiht.rentdetective.app.eval.ComparisonReport;
import io.github.shardkiht.rentdetective.app.service.EvalService;
import io.github.shardkiht.rentdetective.semantic.eval.EvalReport;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;
    private final ComparisonEvalService comparisonEvalService;

    public EvalController(EvalService evalService, ComparisonEvalService comparisonEvalService) {
        this.evalService = evalService;
        this.comparisonEvalService = comparisonEvalService;
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
            List<Long> ids = List.of(listingIds.split(","))
                    .stream().map(String::trim).map(Long::parseLong).toList();
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
            List<Long> ids = List.of(listingIds.split(","))
                    .stream().map(String::trim).map(Long::parseLong).toList();
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
