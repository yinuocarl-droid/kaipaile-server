package com.kaipai.model.refund.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefundApproveDTO {

    private String auditRemark;
}
