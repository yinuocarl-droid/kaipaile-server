package com.kaipai.model.card.entity;
import com.baomidou.mybatisplus.annotation.*; import com.kaipai.common.entity.BaseEntity; import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("share_card_favorite") public class ShareCardFavorite extends BaseEntity { @TableId(type=IdType.AUTO) private Long favoriteId; private Long userId; private Long shareCardId; }
