package com.kaipai.module.model.verify.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class IdentityVerificationListReqDTO {

    private Long userId;
    private Integer status;

    @Min(1)
    private Integer pageNo = 1;

    @Min(1)
    private Integer pageSize = 20;
}
