package com.kaipai.module.model.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_resume_notification_delivery")
public class AiResumeNotificationDelivery extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long deliveryId;
    private String failureId;
    private String failureRequestId;
    private Long userId;
    private Long assignedAdminUserId;
    private String recipientType;
    private Long recipientAdminUserId;
    private String recipientName;
    private String recipientPhone;
    private String recipientEmail;
    private String channelCode;
    private String providerCode;
    private String providerMessageId;
    private String sendSourceType;
    private String sendStatus;
    private LocalDateTime sendRequestedAt;
    private LocalDateTime sentAt;
    private String sendFailureReason;
    private Long sendOperatorAdminUserId;
    private String sendOperatorAdminUserName;
    private String receiptSourceType;
    private String receiptStatus;
    private LocalDateTime receiptAt;
    private String receiptFailureReason;
    private String receiptPayloadJson;
    private Long receiptOperatorAdminUserId;
    private String receiptOperatorAdminUserName;
    private String lastRequestId;
}
