package com.kaipai.model.actor.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("actor_card_background")
public class ActorCardBackground {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属风格 = 风格模板场景码（00-267）；fresh 等无模板旧分类保留，仅后台可见 */
    private String style;

    private String imageUrl;

    private String thumbnailUrl;

    private Integer sortOrder;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}
