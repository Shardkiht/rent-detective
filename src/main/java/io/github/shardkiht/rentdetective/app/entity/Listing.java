package io.github.shardkiht.rentdetective.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("listing")
public class Listing {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String source;

    private String url;

    private String title;

    private String description;

    private Double price;

    private String location;

    private String phone;

    private String status;

    private String riskLevel;

    private String riskTags;
}
