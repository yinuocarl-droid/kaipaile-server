package com.kaipai.module.model.verify.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IdentityVerificationAuditReqDTO {

    @NotBlank
    private String remark;
}
