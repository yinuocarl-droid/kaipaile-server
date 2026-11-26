package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.card.entity.ActorCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActorCardMapper extends BaseMapper<ActorCard> {

    @Select("""
            SELECT *
            FROM actor_card
            WHERE id = #{cardId}
              AND user_id = #{userId}
              AND deleted = 0
            FOR UPDATE
            """)
    ActorCard selectOwnedForUpdate(@Param("userId") Long userId, @Param("cardId") Long cardId);
}
