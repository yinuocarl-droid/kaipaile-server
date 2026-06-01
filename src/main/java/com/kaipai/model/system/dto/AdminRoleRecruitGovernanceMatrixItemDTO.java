package com.kaipai.model.system.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminRoleRecruitGovernanceMatrixItemDTO {

    private Long adminRoleId;
    private String roleCode;
    private String roleName;
    private Integer status;
    private Long boundUserCount;
    private Boolean hasRecruitProjectsPage;
    private Boolean hasRecruitRolesPage;
    private Boolean hasRecruitAppliesPage;
    private Boolean hasRecruitProjectStatusAction;
    private Boolean hasRecruitRoleStatusAction;
    private Boolean hasAdminUsersPage;
    private Boolean pageReady;
    private Boolean actionReady;
    private Boolean pagePermissionGap;
    private Boolean actionPermissionGap;
    private Boolean recruitReady;
    private Boolean recruitPermissionGap;
    private String permissionStage;
    private List<String> missingPermissions;
}


