package com.kaipai.module.model.recruit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RecruitApplySubmitDTO {

    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;

    private String remark;
}
