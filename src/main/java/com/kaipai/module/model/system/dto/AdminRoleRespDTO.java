package com.kaipai.module.model.system.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AdminRoleRespDTO {

    private Long adminRoleId;
    private String roleCode;
    private String roleName;
    private Integer status;
    private String remark;
    private List<String> menuPermissions;
    private List<String> pagePermissions;
    private List<String> actionPermissions;
    private String createUserName;
    private LocalDateTime createTime;
    private String updateUserName;
    private LocalDateTime lastUpdate;
}
