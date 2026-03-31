package com.kaipai.module.model.refund.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefundOperateLogItemDTO {

    private Long logId;
    private Long refundOrderId;
    private Long operatorId;
    private String actionType;
    private String remark;
    private LocalDateTime createTime;
}
