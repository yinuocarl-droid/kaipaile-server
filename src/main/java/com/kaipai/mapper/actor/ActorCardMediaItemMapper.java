package com.kaipai.mapper.actor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.actor.card.entity.ActorCardMediaItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActorCardMediaItemMapper extends BaseMapper<ActorCardMediaItem> {

    @Select("""
            SELECT *
            FROM actor_card_media_item
            WHERE set_id = #{setId}
              AND deleted = 0
            ORDER BY sort_order ASC, item_id ASC
            """)
    List<ActorCardMediaItem> selectActiveBySetId(@Param("setId") Long setId);

    @Update("""
            UPDATE actor_card_media_item
            SET deleted = 1,
                last_update = CURRENT_TIMESTAMP
            WHERE set_id = #{setId}
              AND deleted = 0
            """)
    int deleteActiveBySetId(@Param("setId") Long setId);
}
