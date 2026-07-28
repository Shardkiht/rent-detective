package io.github.shardkiht.rentdetective.rules.relation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * semantic 包专用 Mapper，用于关系规则查询。
 * 直接查询 listings 表（schema.sql 中的实际表名）。
 */
@Mapper
public interface RelationListingMapper {

    /**
     * 统计同一 phone 关联的不同房源数（按 title 去重）。
     */
    @Select("SELECT COUNT(DISTINCT title) FROM listings WHERE phone = #{phone}")
    int countDistinctTitlesByPhone(@Param("phone") String phone);

    /**
     * 查询同一 phone 分组下的所有记录（id, title, price），用于同号不同价检测。
     * 包含 price 为 NULL 的记录，由 Service 层过滤。
     */
    @Select("SELECT id, title, price FROM listings WHERE phone = #{phone}")
    List<Map<String, Object>> selectByPhoneWithPrice(@Param("phone") String phone);
}
