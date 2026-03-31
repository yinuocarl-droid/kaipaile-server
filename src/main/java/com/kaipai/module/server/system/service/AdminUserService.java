package com.kaipai.module.server.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.system.dto.AdminUserBindRolesDTO;
import com.kaipai.module.model.system.dto.AdminUserCreateDTO;
import com.kaipai.module.model.system.dto.AdminUserDetailRespDTO;
import com.kaipai.module.model.system.dto.AdminUserListItemDTO;
import com.kaipai.module.model.system.dto.AdminUserPasswordResetDTO;
import com.kaipai.module.model.system.dto.AdminUserQueryDTO;
import com.kaipai.module.model.system.dto.AdminUserStatusUpdateDTO;
import com.kaipai.module.model.system.dto.AdminUserUpdateDTO;
import com.kaipai.module.model.system.entity.AdminUser;

public interface AdminUserService extends IService<AdminUser> {

    PageResult<AdminUserListItemDTO> adminUserList(AdminUserQueryDTO query);

    AdminUserDetailRespDTO adminUserDetail(Long adminUserId);

    AdminUserDetailRespDTO createAdminUser(AdminUserCreateDTO dto);

    AdminUserDetailRespDTO updateAdminUser(Long adminUserId, AdminUserUpdateDTO dto);

    void updateAdminUserStatus(Long adminUserId, AdminUserStatusUpdateDTO dto);

    AdminUserDetailRespDTO resetAdminUserPassword(Long adminUserId, AdminUserPasswordResetDTO dto);

    AdminUserDetailRespDTO bindUserRoles(Long adminUserId, AdminUserBindRolesDTO dto);
}
