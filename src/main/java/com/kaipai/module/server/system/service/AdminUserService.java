package com.kaipai.module.server.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.system.dto.AdminUserDetailRespDTO;
import com.kaipai.module.model.system.dto.AdminUserListItemDTO;
import com.kaipai.module.model.system.dto.AdminUserQueryDTO;
import com.kaipai.module.model.system.entity.AdminUser;

public interface AdminUserService extends IService<AdminUser> {

    PageResult<AdminUserListItemDTO> adminUserList(AdminUserQueryDTO query);

    AdminUserDetailRespDTO adminUserDetail(Long adminUserId);
}
