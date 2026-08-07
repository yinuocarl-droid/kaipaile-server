package com.kaipai.model.actor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.kaipai.common.entity.BaseEntity;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("actor_media_asset_page")
public class ActorMediaAssetPage extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long pageId;
    private Long assetId;
    private Integer pageNo;
    private String imageObjectKey;
    private String processStatus;
}
