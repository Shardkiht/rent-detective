package io.github.shardkiht.rentdetective.app.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("eval_report")
public class EvalReportEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer total;

    private Integer passed;

    private String detailJson;
}
