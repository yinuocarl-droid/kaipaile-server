package com.kaipai.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActorAiProfileCardTaskMapper extends BaseMapper<ActorAiProfileCardTask> {

    @Select("""
            SELECT *
            FROM actor_ai_profile_card_task
            WHERE actor_card_id = #{cardId}
              AND generation_mode = #{generationMode}
              AND source_fingerprint = #{sourceFingerprint}
              AND deleted = 0
            ORDER BY create_time DESC
            LIMIT 1
            """)
    ActorAiProfileCardTask selectByCardModeAndSource(
            @Param("cardId") Long cardId,
            @Param("generationMode") String generationMode,
            @Param("sourceFingerprint") String sourceFingerprint);
}
