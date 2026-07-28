package io.github.shardkiht.rentdetective.rules.matcher;

import io.github.shardkiht.rentdetective.rules.engine.ListingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 异地 IP 检测。检测发帖 IP 属地与房源城市不一致。
 * rule_type: out_of_region_ip, weight: 0.4
 * 触发案例: 31（IP江苏，房源杭州）, 48（IP福建）, 65（IP广东）
 * 规则来自 104 条人工标注。
 */
@Component
public class OutOfRegionIpMatcher implements RuleMatcher {

    private static final double WEIGHT = 0.4;

    @Override
    public String ruleType() {
        return "out_of_region_ip";
    }

    @Override
    public Optional<RuleHit> match(ListingContext ctx) {
        String ipRegion = ctx.ipRegion();
        if (ipRegion == null || ipRegion.isEmpty()) {
            return Optional.empty();
        }

        // 杭州房源，IP 属地非浙江且非空则命中
        if (!ipRegion.contains("浙江")) {
            return Optional.of(new RuleHit(ruleType(), WEIGHT,
                    "异地 IP: " + ipRegion + "（房源在杭州）"));
        }

        return Optional.empty();
    }
}
