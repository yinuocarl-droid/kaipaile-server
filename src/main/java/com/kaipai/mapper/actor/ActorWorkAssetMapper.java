package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.dto.ActorWorkAssetRespDTO;
import com.kaipai.model.actor.entity.ActorWorkAsset;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActorWorkAssetMapper extends BaseMapper<ActorWorkAsset> {

    @Select("""
            SELECT wa.asset_id AS assetId,
                   wa.usage_code AS usageCode,
                   wa.sort_no AS sortNo,
                   asset.media_type AS mediaType,
                   asset.category_code AS categoryCode,
                   asset.original_name AS originalName,
                   asset.process_status AS processStatus
            FROM actor_work_asset wa
            INNER JOIN actor_media_asset asset
                    ON asset.asset_id = wa.asset_id
                   AND asset.deleted = 0
                   AND asset.user_id = #{userId}
            WHERE wa.experience_id = #{experienceId}
              AND wa.deleted = 0
            ORDER BY CASE wa.usage_code
                         WHEN 'still' THEN 0
                         WHEN 'clip' THEN 1
                         ELSE 2
                     END,
                     wa.sort_no,
                     wa.asset_id
            """)
    List<ActorWorkAssetRespDTO> selectOwnedActiveAssets(
            @Param("userId") Long userId,
            @Param("experienceId") Long experienceId);

    @Update("""
            UPDATE actor_work_asset
            SET deleted = 1, last_update = CURRENT_TIMESTAMP
            WHERE experience_id = #{experienceId} AND deleted = 0
            """)
    int deleteActiveByExperienceId(@Param("experienceId") Long experienceId);
}
