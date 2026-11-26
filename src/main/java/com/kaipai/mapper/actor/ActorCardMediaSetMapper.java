package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.card.entity.ActorCardMediaSet;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActorCardMediaSetMapper extends BaseMapper<ActorCardMediaSet> {

    @Select("""
            <script>
            SELECT *
            FROM actor_card_media_set
            WHERE card_id = #{cardId}
              AND usage_code = #{usageCode}
              AND deleted = 0
              <choose>
                <when test="cardWorkId != null">AND card_work_id = #{cardWorkId}</when>
                <otherwise>AND card_work_id IS NULL</otherwise>
              </choose>
            ORDER BY set_id DESC
            LIMIT 1
            </script>
            """)
    ActorCardMediaSet selectActive(
            @Param("cardId") Long cardId,
            @Param("usageCode") String usageCode,
            @Param("cardWorkId") Long cardWorkId);

    @Select("""
            <script>
            SELECT *
            FROM actor_card_media_set
            WHERE card_id = #{cardId}
              AND usage_code = #{usageCode}
              AND deleted = 0
              <choose>
                <when test="cardWorkId != null">AND card_work_id = #{cardWorkId}</when>
                <otherwise>AND card_work_id IS NULL</otherwise>
              </choose>
            ORDER BY set_id DESC
            LIMIT 1
            FOR UPDATE
            </script>
            """)
    ActorCardMediaSet selectActiveForUpdate(
            @Param("cardId") Long cardId,
            @Param("usageCode") String usageCode,
            @Param("cardWorkId") Long cardWorkId);

    @Select("""
            <script>
            SELECT *
            FROM actor_card_media_set
            WHERE card_id = #{cardId}
              AND usage_code = 'work_still'
              AND deleted = 0
              AND card_work_id IN
              <foreach collection="workIds" item="workId" open="(" separator="," close=")">
                #{workId}
              </foreach>
            ORDER BY set_id
            FOR UPDATE
            </script>
            """)
    List<ActorCardMediaSet> selectActiveWorkSetsForUpdate(
            @Param("cardId") Long cardId,
            @Param("workIds") List<Long> workIds);

    @Select("""
            SELECT *
            FROM actor_card_media_set
            WHERE card_id = #{cardId}
              AND deleted = 0
            ORDER BY set_id
            FOR UPDATE
            """)
    List<ActorCardMediaSet> selectActiveByCardForUpdate(@Param("cardId") Long cardId);

    @Update("""
            UPDATE actor_card_media_set
            SET deleted = 1,
                last_update = CURRENT_TIMESTAMP
            WHERE set_id = #{setId}
              AND deleted = 0
            """)
    int deleteActive(@Param("setId") Long setId);
}
