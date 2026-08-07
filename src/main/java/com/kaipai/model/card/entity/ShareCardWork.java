package com.kaipai.model.card.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.kaipai.common.entity.BaseEntity;
import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("share_card_work")
public class ShareCardWork extends BaseEntity {
    @TableId(type=IdType.AUTO) private Long relationId;
    private Long shareCardId;
    private Long experienceId;
    private Integer sortNo;
}
