package com.kaipai.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_review")
public class OrderReview extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long reviewId;

    private Long orderId;

    private Long reviewerUserId;

    private Long reviewedUserId;

    /** 评价类型: 1剧组评演员, 2演员评剧组 */
    private Integer reviewType;

    /** 评分: 1-5星 */
    private Integer rating;

    private String content;

    /** 是否匿名 */
    private Boolean isAnonymous;

    private String extendedField;
}
