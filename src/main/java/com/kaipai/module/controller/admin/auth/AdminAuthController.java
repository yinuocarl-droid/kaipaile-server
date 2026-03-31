package com.kaipai.module.controller.admin.auth;

import com.kaipai.common.result.R;
import com.kaipai.module.model.adminauth.dto.AdminLoginReqDTO;
import com.kaipai.module.model.adminauth.dto.AdminLoginRespDTO;
import com.kaipai.module.model.adminauth.dto.AdminSessionInfoDTO;
import com.kaipai.module.server.adminauth.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台认证")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "后台账号登录")
    @PostMapping("/login")
    public R<AdminLoginRespDTO> login(@Valid @RequestBody AdminLoginReqDTO dto, HttpServletRequest request) {
        return R.ok(adminAuthService.login(dto, request));
    }

    @Operation(summary = "当前后台登录态")
    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public R<AdminSessionInfoDTO> me() {
        return R.ok(adminAuthService.currentSession());
    }
}
