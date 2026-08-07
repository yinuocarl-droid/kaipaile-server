package com.kaipai.model.actor.entity;
import com.baomidou.mybatisplus.annotation.*; import com.kaipai.common.entity.BaseEntity; import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("actor_media_asset")
public class ActorMediaAsset extends BaseEntity { @TableId(type=IdType.AUTO) private Long assetId; private Long userId; private String mediaType; private String categoryCode; private String storageProvider; private String bucketCode; private String objectKey; private String thumbnailObjectKey; private String originalName; private String mimeType; private Long sizeBytes; private Long durationMs; private Integer pageCount; private String processStatus; private String failureCode; private String failureMessage; private String sourceType; }
