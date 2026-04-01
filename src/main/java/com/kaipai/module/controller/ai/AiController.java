package com.kaipai.module.controller.ai;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.ai.dto.ActorAiQuotaRespDTO;
import com.kaipai.module.server.ai.service.AiQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 配额")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiQuotaService aiQuotaService;

    @Operation(summary = "获取当前用户 AI 配额")
    @GetMapping("/quota")
    public R<ActorAiQuotaRespDTO> quota(Authentication authentication,
                                        @RequestParam(defaultValue = "resume_polish") String type) {
        return R.ok(aiQuotaService.quota(currentUserId(authentication), type));
    }

    @Operation(summary = "消耗一次 AI 简历润色配额")
    @PostMapping("/polish-resume")
    public R<ActorAiQuotaRespDTO> polishResume(Authentication authentication) {
        return R.ok(aiQuotaService.consumeResumePolishQuota(currentUserId(authentication)));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
