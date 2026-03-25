package com.kaipai.module.model.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("credit_score")
public class CreditScore extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long creditScoreId;

    private Long userId;

    /** 当前信用分（初始100） */
    private Integer currentScore;

    private Integer totalEarned;

    private Integer totalDeducted;

    private Integer completeCount;

    /** 鸽单次数 */
    private Integer ghostCount;

    private Integer goodReviewCount;

    private Integer badReviewCount;

    private String extendedField;
}
