package com.kaipai.controller.admin.user;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.model.user.dto.UserAdminDetailDTO;
import com.kaipai.model.user.dto.UserAdminListItemDTO;
import com.kaipai.model.user.dto.UserAdminQueryDTO;
import com.kaipai.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台用户中心")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "业务用户列表")
    @GetMapping
    @PreAuthorize("hasAuthority('page.users.index')")
    public R<PageResult<UserAdminListItemDTO>> list(@Valid UserAdminQueryDTO query) {
        return R.ok(userService.adminUserList(query));
    }

    @Operation(summary = "业务用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('page.users.detail')")
    public R<UserAdminDetailDTO> detail(@PathVariable Long id) {
        return R.ok(userService.adminUserDetail(id));
    }
}
