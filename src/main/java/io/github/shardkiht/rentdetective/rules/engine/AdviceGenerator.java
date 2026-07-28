package io.github.shardkiht.rentdetective.rules.engine;

import io.github.shardkiht.rentdetective.rules.matcher.RuleHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 根据缺失项和弱信号生成建议。
 * 映射表来自规则清单第五节。规则来自 104 条人工标注。
 */
@Component
public class AdviceGenerator {

    /**
     * 根据缺失项和弱信号生成建议列表。
     *
     * @param missingItems 缺失的核心信息项（price/contact/body/location）
     * @param weakSignals  命中的弱信号规则
     * @return 建议列表
     */
    public List<String> generate(Set<String> missingItems, List<RuleHit> weakSignals) {
        List<String> advice = new ArrayList<>();

        if (missingItems.contains("price")) {
            advice.add("私信问价时对比同小区均价；对方报价后说'这套没了推荐另一套'→ 低价引流实锤");
        }
        if (missingItems.contains("contact")) {
            advice.add("仅微信无电话 → 添加后看朋友圈是否全是不同小区房源（是→中介）；要求电话沟通，支吾不给→高危");
        }
        if (missingItems.contains("body")) {
            advice.add("要求提供带当天日期的实拍视频；拒绝或只发精修图→假房源概率高");
        }
        if (missingItems.contains("location")) {
            advice.add("无位置信息 → 询问具体地址，无法提供或含糊其辞→可疑");
        }

        // 根据弱信号补充建议
        for (RuleHit hit : weakSignals) {
            switch (hit.ruleType()) {
                case "out_of_region_ip" ->
                        advice.add("询问能否当天看房，异地托管通常无法当天安排");
                case "agent_stock_phrase" ->
                        advice.add("催单话术特征，任何'先交定金锁房'要求都是诈骗标准动作");
                default -> { /* 其他弱信号不额外生成建议 */ }
            }
        }

        return advice;
    }
}
