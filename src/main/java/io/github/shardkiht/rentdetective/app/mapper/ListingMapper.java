package io.github.shardkiht.rentdetective.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.shardkiht.rentdetective.app.entity.Listing;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ListingMapper extends BaseMapper<Listing> {
}
