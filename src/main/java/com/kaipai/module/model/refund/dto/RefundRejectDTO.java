package com.kaipai.module.model.refund.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefundRejectDTO {

    @NotBlank
    private String auditRemark;
}
