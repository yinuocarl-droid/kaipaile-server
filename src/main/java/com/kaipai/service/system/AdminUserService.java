package com.kaipai.service.system;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.system.dto.AdminUserBindRolesDTO;
import com.kaipai.model.system.dto.AdminUserCreateDTO;
import com.kaipai.model.system.dto.AdminUserDetailRespDTO;
import com.kaipai.model.system.dto.AdminUserListItemDTO;
import com.kaipai.model.system.dto.AdminUserPasswordResetDTO;
import com.kaipai.model.system.dto.AdminUserQueryDTO;
import com.kaipai.model.system.dto.AdminUserStatusUpdateDTO;
import com.kaipai.model.system.dto.AdminUserUpdateDTO;
import com.kaipai.model.system.entity.AdminUser;

public interface AdminUserService extends IService<AdminUser> {

    PageResult<AdminUserListItemDTO> adminUserList(AdminUserQueryDTO query);

    AdminUserDetailRespDTO adminUserDetail(Long adminUserId);

    AdminUserDetailRespDTO createAdminUser(AdminUserCreateDTO dto);

    AdminUserDetailRespDTO updateAdminUser(Long adminUserId, AdminUserUpdateDTO dto);

    void updateAdminUserStatus(Long adminUserId, AdminUserStatusUpdateDTO dto);

    AdminUserDetailRespDTO resetAdminUserPassword(Long adminUserId, AdminUserPasswordResetDTO dto);

    AdminUserDetailRespDTO bindUserRoles(Long adminUserId, AdminUserBindRolesDTO dto);
}


