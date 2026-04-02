package com.kaipai.module.model.system.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminRoleRecruitGovernanceMatrixItemDTO {

    private Long adminRoleId;
    private String roleCode;
    private String roleName;
    private Integer status;
    private Long boundUserCount;
    private Boolean hasRecruitMenu;
    private Boolean hasRecruitProjectsPage;
    private Boolean hasRecruitRolesPage;
    private Boolean hasRecruitAppliesPage;
    private Boolean hasRecruitProjectStatusAction;
    private Boolean hasRecruitRoleStatusAction;
    private Boolean hasAdminUsersPage;
    private Boolean recruitReady;
    private Boolean reliesOnFallback;
    private String rolloutStage;
    private List<String> missingPermissions;
}
