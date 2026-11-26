package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.entity.ActorMediaAssetPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActorMediaAssetPageMapper extends BaseMapper<ActorMediaAssetPage> {
    @Delete("DELETE FROM actor_media_asset_page WHERE asset_id = #{assetId} AND deleted = 0")
    int deleteActiveByAssetId(@Param("assetId") Long assetId);
}
