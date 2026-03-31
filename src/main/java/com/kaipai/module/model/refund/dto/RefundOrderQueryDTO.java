package com.kaipai.module.model.refund.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RefundOrderQueryDTO {

    @Min(1)
    private int pageNo = 1;
    @Min(1)
    private int pageSize = 20;
    private String refundNo;
    private Long userId;
    private Integer auditStatus;
    private Integer refundStatus;
}
