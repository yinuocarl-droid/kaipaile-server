package com.kaipai.module.model.membership.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MembershipProductQueryDTO {

    @Min(1)
    private int pageNo = 1;
    @Min(1)
    private int pageSize = 20;
    private Integer membershipTier;
    private Integer status;
}
