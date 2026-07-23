package com.kaipai.controller.api.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO;
import com.kaipai.model.ai.dto.ProfileImportApplyRespDTO;
import com.kaipai.service.ai.ProfileImportApplyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/actor/profile-import")
@RequiredArgsConstructor
public class ActorProfileImportController {
    private final ProfileImportApplyService service;

    @PostMapping("/apply")
    public R<ProfileImportApplyRespDTO> apply(Authentication authentication,
            @Valid @RequestBody ProfileImportApplyReqDTO request) {
        return R.ok(service.apply(userId(authentication), request));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new BizException("未登录或登录态失效");
        }
        return id;
    }
}
