package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.entity.ActorProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActorProfileMapper extends BaseMapper<ActorProfile> {
    @Update("UPDATE actor_profile SET work_library_version = work_library_version + 1 WHERE actor_profile_id = #{profileId} AND deleted = 0")
    int incrementWorkLibraryVersion(@Param("profileId") Long profileId);
}
