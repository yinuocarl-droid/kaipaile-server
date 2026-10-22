package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.card.entity.ActorCardWork;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActorCardWorkMapper extends BaseMapper<ActorCardWork> {

    @Select("""
            SELECT *
            FROM actor_card_work
            WHERE id = #{workId}
              AND card_id = #{cardId}
              AND deleted = 0
            """)
    ActorCardWork selectActiveByIdAndCard(
            @Param("cardId") Long cardId,
            @Param("workId") Long workId);
}
