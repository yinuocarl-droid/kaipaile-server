package com.kaipai.model.actor.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_card_work")
public class ActorCardWork extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cardId;

    /** 来源演艺经历 id，新增作品时为 null */
    private Long sourceWorkId;

    private String workTitle;

    /** 作品类型: short_drama|micro_film|tv|movie|other */
    private String workType;

    private String roleName;

    /** 剧照 URL 数组 JSON，最多 3 张，第一张为封面 */
    private String stillsJson;

    private Integer sortOrder;
}
