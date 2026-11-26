package com.kaipai.controller.api.ai;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.ai.dto.ProfileImportCapabilityRespDTO;
import com.kaipai.model.ai.dto.ProfileImportExtractReqDTO;
import com.kaipai.model.ai.dto.ProfileImportExtractionRespDTO;
import com.kaipai.service.ai.ProfileImportConfigService;
import com.kaipai.service.ai.ProfileImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/profile-import")
@RequiredArgsConstructor
public class AiProfileImportController {
    private final ProfileImportService service;
    private final ProfileImportConfigService configService;

    @GetMapping("/capability")
    public R<ProfileImportCapabilityRespDTO> capability(Authentication authentication) {
        userId(authentication);
        return R.ok(configService.capability());
    }

    @PostMapping("/extract")
    public R<ProfileImportExtractionRespDTO> extract(Authentication authentication,
            @Valid @RequestBody ProfileImportExtractReqDTO request) {
        return R.ok(service.extract(userId(authentication), request));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new BizException("未登录或登录态失效");
        }
        return id;
    }
}
