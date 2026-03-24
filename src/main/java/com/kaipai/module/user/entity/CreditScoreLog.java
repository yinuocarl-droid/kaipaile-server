package com.kaipai.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("credit_score_log")
public class CreditScoreLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long logId;

    private Long userId;

    /** 分值变化量（正数加分，负数扣分） */
    private Integer scoreChange;

    private Integer scoreBefore;

    private Integer scoreAfter;

    /** 变动类型: 1完成订单, 2鸽单, 3好评加分, 4差评扣分, 5系统调整 */
    private Integer changeType;

    private Long relatedOrderId;

    private String remark;

    private String extendedField;
}
