package com.kaipai.model.verify.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IdentityVerificationListReqDTO {

    private Long userId;
    private Integer status;
    private LocalDateTime submitTimeFrom;
    private LocalDateTime submitTimeTo;

    @Min(1)
    private Integer pageNo = 1;

    @Min(1)
    private Integer pageSize = 20;
}
