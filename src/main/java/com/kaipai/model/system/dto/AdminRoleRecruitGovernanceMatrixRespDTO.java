package com.kaipai.model.system.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminRoleRecruitGovernanceMatrixRespDTO {

    private Integer totalRoleCount;
    private Integer enabledRoleCount;
    private Integer recruitReadyRoleCount;
    private Integer pageReadyRoleCount;
    private Integer actionReadyRoleCount;
    private Integer recruitPermissionGapRoleCount;
    private Integer pagePermissionGapRoleCount;
    private Integer actionPermissionGapRoleCount;
    private Integer pendingRoleCount;
    private Long recruitPermissionGapBoundUserCount;
    private Boolean pagePermissionGapCleared;
    private Boolean actionPermissionGapCleared;
    private Boolean recruitPermissionGapCleared;
    private List<AdminRoleRecruitGovernanceMatrixItemDTO> list;
}


