package com.kaipai.module.model.membership.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MembershipProductSortDTO {

    @NotNull
    private Integer sortNo;
}
