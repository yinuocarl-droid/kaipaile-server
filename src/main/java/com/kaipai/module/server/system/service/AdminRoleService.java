package com.kaipai.module.server.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.system.dto.AdminRoleAiGovernanceMatrixRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleCopyDTO;
import com.kaipai.module.model.system.dto.AdminRoleQueryDTO;
import com.kaipai.module.model.system.dto.AdminRoleRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleSaveDTO;
import com.kaipai.module.model.system.dto.AdminRoleStatusChangeDTO;
import com.kaipai.module.model.system.entity.AdminRole;

public interface AdminRoleService extends IService<AdminRole> {

    PageResult<AdminRoleRespDTO> adminRoleList(AdminRoleQueryDTO query);

    AdminRoleRespDTO adminRoleDetail(Long adminRoleId);

    AdminRoleAiGovernanceMatrixRespDTO aiGovernanceMatrix();

    AdminRoleRespDTO createRole(AdminRoleSaveDTO dto);

    AdminRoleRespDTO updateRole(Long adminRoleId, AdminRoleSaveDTO dto);

    AdminRoleRespDTO changeRoleStatus(AdminRoleStatusChangeDTO dto);

    AdminRoleRespDTO copyRole(AdminRoleCopyDTO dto);
}
