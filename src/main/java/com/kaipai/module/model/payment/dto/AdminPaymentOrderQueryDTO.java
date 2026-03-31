package com.kaipai.module.model.payment.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class AdminPaymentOrderQueryDTO {

    @Min(1)
    private int pageNo = 1;

    @Min(1)
    private int pageSize = 20;

    private String orderNo;

    private Long userId;

    private Integer payStatus;

    private String payChannel;

    private String bizType;

    private Long productId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createTimeFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createTimeTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime paidTimeFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime paidTimeTo;
}
