package io.github.shardkiht.rentdetective.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("eval_case")
public class EvalCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String inputJson;

    private String expectedVerdict;
}
