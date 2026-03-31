package com.kaipai.module.model.membership.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MembershipAccountExtendDTO {

    @NotNull
    private LocalDateTime expireTime;
    private String remark;
}
