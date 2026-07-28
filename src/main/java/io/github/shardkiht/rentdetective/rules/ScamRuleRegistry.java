package io.github.shardkiht.rentdetective.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.domain.entity.ScamRule;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则注册表。启动时从 classpath:scam_rules.json 加载规则并按 ruleType 建索引。
 * 规则来自 104 条人工标注，规则引擎为确定性打分。
 */
@Component
public class ScamRuleRegistry {

    private Map<String, ScamRule> rulesByType;

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource("scam_rules.json");
            try (InputStream is = resource.getInputStream()) {
                List<ScamRule> rules = mapper.readValue(is, new TypeReference<>() {
                });
                rulesByType = rules.stream()
                        .collect(Collectors.toMap(ScamRule::getRuleType, r -> r, (a, b) -> a, LinkedHashMap::new));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load scam_rules.json", e);
        }
    }

    /** 获取所有启用的规则 */
    public List<ScamRule> getEnabledRules() {
        return rulesByType.values().stream()
                .filter(ScamRule::isEnabled)
                .collect(Collectors.toList());
    }

}
