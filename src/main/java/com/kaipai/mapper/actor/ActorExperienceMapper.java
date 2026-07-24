package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.entity.ActorExperience;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActorExperienceMapper extends BaseMapper<ActorExperience> {

    @Select("""
            SELECT *
            FROM actor_experience
            WHERE user_id = #{userId}
              AND experience_id = #{experienceId}
              AND deleted = 0
            """)
    ActorExperience selectOwnedActiveById(
            @Param("userId") Long userId,
            @Param("experienceId") Long experienceId);

    @Select("""
            SELECT *
            FROM actor_experience
            WHERE user_id = #{userId}
              AND experience_id = #{experienceId}
              AND deleted = 0
            FOR UPDATE
            """)
    ActorExperience selectOwnedActiveByIdForUpdate(
            @Param("userId") Long userId,
            @Param("experienceId") Long experienceId);
}
