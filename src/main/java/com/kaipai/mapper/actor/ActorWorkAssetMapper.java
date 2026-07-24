package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.entity.ActorWorkAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActorWorkAssetMapper extends BaseMapper<ActorWorkAsset> {

    @Update("""
            UPDATE actor_work_asset
            SET deleted = 1, last_update = CURRENT_TIMESTAMP
            WHERE experience_id = #{experienceId} AND deleted = 0
            """)
    int deleteActiveByExperienceId(@Param("experienceId") Long experienceId);
}
