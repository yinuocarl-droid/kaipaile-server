package com.kaipai.module.model.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cooperation_order")
public class CooperationOrder extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long orderId;

    private String orderNo;

    private Long crewUserId;

    private Long crewProfileId;

    private Long actorUserId;

    private Long actorProfileId;

    /** 来源类型: 1来自招募投递, 2来自邀约 */
    private Integer sourceType;

    private Long sourceId;

    private String dramaName;

    private String roleName;

    private LocalDateTime shootStartTime;

    private LocalDateTime shootEndTime;

    private String shootProvince;

    private String shootCity;

    private String salary;

    /** 订单状态: 1进行中, 2待确认完成, 3已完成, 4已取消, 5纠纷中 */
    private Integer orderStatus;

    private Boolean crewConfirmed;

    private Boolean actorConfirmed;

    private LocalDateTime crewConfirmTime;

    private LocalDateTime actorConfirmTime;

    private String cancelReason;

    private String extendedField;
}
