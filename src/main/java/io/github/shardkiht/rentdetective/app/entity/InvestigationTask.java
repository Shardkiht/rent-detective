package io.github.shardkiht.rentdetective.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("investigation_task")
public class InvestigationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long listingId;

    private String status;

    private String resultJson;
}
