package com.kaipai.controller.api.verify;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.verify.dto.IdentityVerificationStatusRespDTO;
import com.kaipai.model.verify.dto.IdentityVerificationSubmitReqDTO;
import com.kaipai.service.verify.IdentityVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "实名认证")
@RestController
@RequestMapping("/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final IdentityVerificationService identityVerificationService;

    @Operation(summary = "查询当前实名状态")
    @GetMapping("/status")
    public R<IdentityVerificationStatusRespDTO> status(Authentication authentication) {
        return R.ok(identityVerificationService.currentStatus(currentUserId(authentication)));
    }

    @Operation(summary = "提交实名申请")
    @PostMapping("/submit")
    public R<IdentityVerificationStatusRespDTO> submit(Authentication authentication,
                                                      @Valid @RequestBody IdentityVerificationSubmitReqDTO req) {
        return R.ok(identityVerificationService.submit(currentUserId(authentication), req));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
