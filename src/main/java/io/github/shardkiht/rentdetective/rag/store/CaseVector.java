package io.github.shardkiht.rentdetective.rag.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * case_vectors 表实体 —— 存储每条房源的向量化结果。
 * Agent 循环手写，工具可插拔；识坑规则来自人工标注。
 */
@Data
@TableName("case_vectors")
public class CaseVector {

    @TableId(type = IdType.INPUT)
    private Integer listingId;

    private String embeddedText;

    private String vectorJson;

    private LocalDateTime createdAt;
}
