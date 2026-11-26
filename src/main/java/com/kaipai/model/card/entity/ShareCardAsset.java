package com.kaipai.model.card.entity;
import com.baomidou.mybatisplus.annotation.*; import com.kaipai.common.entity.BaseEntity; import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("share_card_asset") public class ShareCardAsset extends BaseEntity { @TableId(type=IdType.AUTO) private Long relationId; private Long shareCardId; private Long assetId; private String usageCode; private Integer sortNo; }
