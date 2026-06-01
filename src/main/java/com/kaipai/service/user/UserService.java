package com.kaipai.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.user.dto.UserAdminDetailDTO;
import com.kaipai.model.user.dto.UserAdminListItemDTO;
import com.kaipai.model.user.dto.UserAdminQueryDTO;
import com.kaipai.model.user.dto.UserSessionRespDTO;
import com.kaipai.model.user.entity.User;

public interface UserService extends IService<User> {

    UserSessionRespDTO currentUser(Long userId);

    UserSessionRespDTO updateCurrentUserRole(Long userId, Integer userType);

    PageResult<UserAdminListItemDTO> adminUserList(UserAdminQueryDTO query);

    UserAdminDetailDTO adminUserDetail(Long userId);
}
