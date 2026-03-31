package com.kaipai.module.controller.admin.system;

import com.kaipai.module.server.system.service.AdminOperationLogService;
import com.kaipai.module.server.system.service.AdminRoleService;
import com.kaipai.module.server.system.service.AdminUserRoleService;
import com.kaipai.module.server.system.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台系统管理")
@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
public class AdminSystemController {

    private final AdminUserService adminUserService;
    private final AdminRoleService adminRoleService;
    private final AdminUserRoleService adminUserRoleService;
    private final AdminOperationLogService adminOperationLogService;
}
