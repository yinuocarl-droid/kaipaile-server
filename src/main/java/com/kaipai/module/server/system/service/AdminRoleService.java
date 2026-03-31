package com.kaipai.module.server.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.system.dto.AdminRoleQueryDTO;
import com.kaipai.module.model.system.dto.AdminRoleRespDTO;
import com.kaipai.module.model.system.dto.AdminRoleSaveDTO;
import com.kaipai.module.model.system.entity.AdminRole;

public interface AdminRoleService extends IService<AdminRole> {

    PageResult<AdminRoleRespDTO> adminRoleList(AdminRoleQueryDTO query);

    AdminRoleRespDTO createRole(AdminRoleSaveDTO dto);

    AdminRoleRespDTO updateRole(Long adminRoleId, AdminRoleSaveDTO dto);
}
