package com.kaipai.module.model.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminRecruitRoleStatusChangeDTO {

    @NotBlank
    private String status;

    private String reason;
}
