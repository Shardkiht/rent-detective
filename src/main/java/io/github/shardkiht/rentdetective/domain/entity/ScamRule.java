package io.github.shardkiht.rentdetective.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 欺诈规则实体。规则来自 104 条人工标注，规则引擎为确定性打分。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("scam_rule")
public class ScamRule {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("ruleType")
    private String ruleType;

    @JsonProperty("pattern")
    private String pattern;

    @JsonProperty("weight")
    private double weight;

    @JsonProperty("note")
    private String note;

    @JsonProperty("enabled")
    private boolean enabled;
}
