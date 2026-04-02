package com.kaipai.module.model.system.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminRoleAiGovernanceMatrixRespDTO {

    private Integer totalRoleCount;
    private Integer enabledRoleCount;
    private Integer aiReadyRoleCount;
    private Integer fallbackRoleCount;
    private Integer pendingRoleCount;
    private Long fallbackBoundUserCount;
    private Boolean canRetireFallback;
    private List<AdminRoleAiGovernanceMatrixItemDTO> list;
}
