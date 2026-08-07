package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Update("""
            UPDATE actor_media_asset
            SET process_status = 'ready',
                page_count = #{pageCount},
                failure_code = NULL,
                failure_message = NULL,
                last_update = CURRENT_TIMESTAMP
            WHERE asset_id = #{assetId}
              AND user_id = #{userId}
              AND deleted = 0
              AND process_status = 'processing'
            """)
    int markReady(
            @Param("assetId") Long assetId,
            @Param("userId") Long userId,
            @Param("pageCount") Integer pageCount);

    @Update("""
            UPDATE actor_media_asset
            SET process_status = 'failed',
                page_count = NULL,
                failure_code = #{failureCode},
                failure_message = #{failureMessage},
                last_update = CURRENT_TIMESTAMP
            WHERE asset_id = #{assetId}
              AND user_id = #{userId}
              AND deleted = 0
              AND process_status = 'processing'
            """)
    int markFailed(
            @Param("assetId") Long assetId,
            @Param("userId") Long userId,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage);
}
