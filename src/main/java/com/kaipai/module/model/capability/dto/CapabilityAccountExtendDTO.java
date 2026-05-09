package com.kaipai.module.model.capability.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CapabilityAccountExtendDTO {

    @NotNull
    private LocalDateTime expireTime;
    private String remark;
}
