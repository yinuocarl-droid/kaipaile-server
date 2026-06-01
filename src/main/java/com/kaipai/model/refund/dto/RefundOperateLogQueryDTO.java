package com.kaipai.model.refund.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefundOperateLogQueryDTO {

    @Min(1)
    private long pageNo = 1;

    @Min(1)
    private long pageSize = 20;

    private Long refundOrderId;
    private String refundNo;
    private Long operatorId;
    private String actionType;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
}
