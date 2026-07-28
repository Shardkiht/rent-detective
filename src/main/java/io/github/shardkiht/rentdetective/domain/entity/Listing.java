package io.github.shardkiht.rentdetective.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("listings")
public class Listing {

    @TableId(type = IdType.INPUT)
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

    private String evalGroup;

    private Integer multiTierPricing;

    private String labelNote;
}