package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActorMediaAssetMapper extends BaseMapper<ActorMediaAsset> {

    @Select("""
            <script>
            SELECT *
            FROM actor_media_asset
            WHERE user_id = #{userId}
              AND deleted = 0
              AND asset_id IN
              <foreach collection="assetIds" item="assetId" open="(" separator="," close=")">
                #{assetId}
              </foreach>
            ORDER BY asset_id
            FOR UPDATE
            </script>
            """)
    List<ActorMediaAsset> selectOwnedActiveByIdsForUpdate(
            @Param("userId") Long userId,
            @Param("assetIds") List<Long> assetIds);
}
