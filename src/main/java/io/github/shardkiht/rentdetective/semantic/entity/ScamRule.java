package io.github.shardkiht.rentdetective.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("scam_rule")
public class ScamRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;

    private String pattern;

    private String description;

    private Boolean enabled;
}
