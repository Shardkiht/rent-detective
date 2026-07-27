package io.github.shardkiht.rentdetective.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.shardkiht.rentdetective.app.entity.Listing;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ListingMapper extends BaseMapper<Listing> {

    @Select("SELECT COUNT(*) FROM listings WHERE id = #{id}")
    boolean existsById(@Param("id") Long id);

    @Insert("""
            INSERT INTO listings (id, title, price, location, description, phone,
                                  source, risk_level, risk_tags, eval_group,
                                  multi_tier_pricing, label_note)
            VALUES (#{id}, #{title}, #{price}, #{location}, #{description}, #{phone},
                    #{source}, #{riskLevel}, #{riskTags}, #{evalGroup},
                    #{multiTierPricing}, #{labelNote})
            """)
    int insertCustom(Listing listing);
}
