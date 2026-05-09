package com.kaipai.module.model.system.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminRoleAiGovernanceMatrixItemDTO {

    private Long adminRoleId;
    private String roleCode;
    private String roleName;
    private Integer status;
    private Long boundUserCount;
    private Boolean hasAiGovernancePage;
    private Boolean hasOperationLogsPage;
    private Boolean hasAiReviewAction;
    private Boolean hasAiResolveAction;
    private Boolean aiReady;
    private Boolean operationLogsPermissionGap;
    private String permissionStage;
    private List<String> missingPermissions;
}


