package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.entity.ActorProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface ActorProfileMapper extends BaseMapper<ActorProfile> {
    @Select("""
            SELECT actor_profile_id, user_id, version, work_library_version,
                   nick_name, gender, age, height, location_city, weight,
                   origin_place, school_name, major_name,
                   language_tags_json, specialty_tags_json, role_type_tags_json,
                   professional_ability_tags_json, intro,
                   birth_year, birth_month, birth_day, birth_precision
            FROM actor_profile
            WHERE user_id = #{userId}
              AND deleted = 0
            ORDER BY actor_profile_id DESC
            LIMIT 2
            """)
    @Results(id = "profileImportActorProfile", value = {
            @Result(column = "birth_day", property = "birthDayOfMonth")
    })
    List<ActorProfile> selectImportContextsByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT *
            FROM actor_profile
            WHERE user_id = #{userId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    @ResultMap("profileImportActorProfile")
    ActorProfile selectByUserIdForUpdate(@Param("userId") Long userId);

    @Update("UPDATE actor_profile SET work_library_version = work_library_version + 1 WHERE actor_profile_id = #{profileId} AND deleted = 0")
    int incrementWorkLibraryVersion(@Param("profileId") Long profileId);

    @Update("""
            UPDATE actor_profile
            SET work_library_version = work_library_version + 1
            WHERE actor_profile_id = #{profileId}
              AND work_library_version = #{expectedVersion}
              AND deleted = 0
            """)
    int incrementWorkLibraryVersionIfExpected(
            @Param("profileId") Long profileId,
            @Param("expectedVersion") Long expectedVersion);
}
