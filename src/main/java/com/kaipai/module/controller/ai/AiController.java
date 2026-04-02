package com.kaipai.module.controller.ai;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.ai.dto.ActorAiQuotaRespDTO;
import com.kaipai.module.model.ai.dto.AiResumeErrorCode;
import com.kaipai.module.model.ai.dto.AiResumeHistoryItemDTO;
import com.kaipai.module.model.ai.dto.AiResumePolishReqDTO;
import com.kaipai.module.model.ai.dto.AiResumeRollbackReqDTO;
import com.kaipai.module.model.ai.dto.AiResumeRollbackRespDTO;
import com.kaipai.module.server.ai.service.AiQuotaService;
import com.kaipai.module.server.ai.service.AiResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 能力")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiQuotaService aiQuotaService;
    private final AiResumeService aiResumeService;

    @Operation(summary = "获取当前用户 AI 配额")
    @GetMapping("/quota")
    public R<ActorAiQuotaRespDTO> quota(Authentication authentication,
                                        @RequestParam(defaultValue = "resume_polish") String type) {
        return R.ok(aiQuotaService.quota(currentUserId(authentication), type));
    }

    @Operation(summary = "生成 AI 简历润色 patch")
    @PostMapping("/polish-resume")
    public R<?> polishResume(Authentication authentication,
                             @RequestBody(required = false) AiResumePolishReqDTO dto) {
        Long userId = currentUserId(authentication);
        if (isLegacyConsumeRequest(dto)) {
            return R.ok(aiQuotaService.consumeResumePolishQuota(userId));
        }
        return R.ok(aiResumeService.polishResume(userId, dto));
    }

    @Operation(summary = "获取 AI 简历润色历史")
    @GetMapping("/resume-polish/history")
    public R<PageResult<AiResumeHistoryItemDTO>> history(Authentication authentication,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return R.ok(aiResumeService.history(currentUserId(authentication), page, size));
    }

    @Operation(summary = "回滚 AI 简历润色历史")
    @PostMapping("/resume-polish/history/{historyId}/rollback")
    public R<AiResumeRollbackRespDTO> rollback(Authentication authentication,
                                               @PathVariable String historyId,
                                               @RequestBody(required = false) AiResumeRollbackReqDTO dto) {
        return R.ok(aiResumeService.rollback(currentUserId(authentication), historyId, dto == null ? new AiResumeRollbackReqDTO() : dto));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException(AiResumeErrorCode.AUTH_REQUIRED, "未登录或登录态失效");
        }
        return userId;
    }

    private boolean isLegacyConsumeRequest(AiResumePolishReqDTO dto) {
        return dto == null
                || (!StringUtils.hasText(dto.getInstruction())
                && (dto.getContext() == null || dto.getContext().getEditableFields() == null || dto.getContext().getEditableFields().isEmpty()));
    }
}
