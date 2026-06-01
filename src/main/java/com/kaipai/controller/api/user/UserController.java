package com.kaipai.controller.api.user;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.user.dto.UserRoleUpdateReqDTO;
import com.kaipai.model.user.dto.UserSessionRespDTO;
import com.kaipai.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public R<UserSessionRespDTO> me(Authentication authentication) {
        return R.ok(userService.currentUser(currentUserId(authentication)));
    }

    @Operation(summary = "切换当前登录用户身份")
    @PutMapping("/role")
    public R<UserSessionRespDTO> updateRole(Authentication authentication, @Valid @RequestBody UserRoleUpdateReqDTO dto) {
        return R.ok(userService.updateCurrentUserRole(currentUserId(authentication), dto.getUserType()));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
