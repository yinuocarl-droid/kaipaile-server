package com.kaipai.module.model.system.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminRoleRecruitGovernanceMatrixRespDTO {

    private Integer totalRoleCount;
    private Integer enabledRoleCount;
    private Integer recruitReadyRoleCount;
    private Integer fallbackRoleCount;
    private Integer pendingRoleCount;
    private Long fallbackBoundUserCount;
    private Boolean canRetireFallback;
    private List<AdminRoleRecruitGovernanceMatrixItemDTO> list;
}
