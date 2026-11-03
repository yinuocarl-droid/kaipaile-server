package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.card.entity.ActorCardVisualInput;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActorCardVisualInputMapper extends BaseMapper<ActorCardVisualInput> {

    @Select("""
            SELECT * FROM actor_card_visual_input WHERE card_id = #{cardId}
            """)
    ActorCardVisualInput selectByCardId(@Param("cardId") Long cardId);

    @Select("""
            SELECT * FROM actor_card_visual_input WHERE card_id = #{cardId} FOR UPDATE
            """)
    ActorCardVisualInput selectByCardIdForUpdate(@Param("cardId") Long cardId);

    @Update("""
            UPDATE actor_card_visual_input
            SET generation_mode = #{value.generationMode},
                person_source_type = #{value.personSourceType},
                person_asset_id = #{value.personAssetId},
                person_image_url = #{value.personImageUrl},
                background_id = #{value.backgroundId},
                background_image_url = #{value.backgroundImageUrl},
                revision = #{value.revision},
                input_fingerprint = #{value.inputFingerprint},
                generation_config_version = #{value.generationConfigVersion},
                generated_image_url = NULL,
                generated_fingerprint = NULL,
                latest_task_id = NULL,
                last_update = CURRENT_TIMESTAMP
            WHERE card_id = #{value.cardId}
              AND user_id = #{value.userId}
              AND revision = #{expectedRevision}
            """)
    int updateInputCas(
            @Param("value") ActorCardVisualInput value,
            @Param("expectedRevision") long expectedRevision);

    @Update("""
            UPDATE actor_card_visual_input
            SET generated_image_url = #{generatedImageUrl},
                generated_fingerprint = #{fingerprint},
                latest_task_id = #{taskId},
                last_update = CURRENT_TIMESTAMP
            WHERE card_id = #{cardId}
              AND user_id = #{userId}
              AND input_fingerprint = #{fingerprint}
            """)
    int applyResultIfCurrent(
            @Param("userId") Long userId,
            @Param("cardId") Long cardId,
            @Param("fingerprint") String fingerprint,
            @Param("taskId") String taskId,
            @Param("generatedImageUrl") String generatedImageUrl);

    @Update("""
            UPDATE actor_card_visual_input
            SET latest_task_id = #{taskId}, last_update = CURRENT_TIMESTAMP
            WHERE card_id = #{cardId}
              AND user_id = #{userId}
              AND input_fingerprint = #{fingerprint}
            """)
    int attachTaskIfCurrent(
            @Param("userId") Long userId,
            @Param("cardId") Long cardId,
            @Param("fingerprint") String fingerprint,
            @Param("taskId") String taskId);
}
