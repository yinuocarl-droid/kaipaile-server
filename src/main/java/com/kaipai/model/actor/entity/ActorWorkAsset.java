package com.kaipai.model.actor.entity;
import com.baomidou.mybatisplus.annotation.*; import com.kaipai.common.entity.BaseEntity; import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("actor_work_asset") public class ActorWorkAsset extends BaseEntity { @TableId(type=IdType.AUTO) private Long relationId; private Long experienceId; private Long assetId; private String usageCode; private Integer sortNo; }
