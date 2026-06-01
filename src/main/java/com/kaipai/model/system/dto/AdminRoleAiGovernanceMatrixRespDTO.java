package com.kaipai.model.system.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminRoleAiGovernanceMatrixRespDTO {

    private Integer totalRoleCount;
    private Integer enabledRoleCount;
    private Integer aiReadyRoleCount;
    private Integer operationLogsPermissionGapRoleCount;
    private Integer pendingRoleCount;
    private Long operationLogsPermissionGapBoundUserCount;
    private Boolean operationLogsPermissionGapCleared;
    private List<AdminRoleAiGovernanceMatrixItemDTO> list;
}


