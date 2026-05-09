package com.kaipai.module.controller.ai;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.ai.dto.AiProfileCardGenerateReqDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardGenerateRespDTO;
import com.kaipai.module.model.ai.dto.AiProfileCardTaskRespDTO;
import com.kaipai.module.model.ai.dto.AiResumeErrorCode;
import com.kaipai.module.server.ai.service.AiProfileCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI 分享图")
@RestController
@RequestMapping("/ai/profile-card")
@RequiredArgsConstructor
public class AiProfileCardController {

    private final AiProfileCardService aiProfileCardService;

    @Operation(summary = "提交 AI 分享图生成任务")
    @PostMapping("/generate")
    public R<AiProfileCardGenerateRespDTO> generate(Authentication authentication,
                                                    @Valid @RequestBody AiProfileCardGenerateReqDTO dto) {
        return R.ok(aiProfileCardService.generate(currentUserId(authentication), dto));
    }

    @Operation(summary = "查询我的 AI 分享图生成任务列表")
    @GetMapping("/tasks")
    public R<List<AiProfileCardTaskRespDTO>> tasks(Authentication authentication) {
        return R.ok(aiProfileCardService.tasks(currentUserId(authentication)));
    }

    @Operation(summary = "查询 AI 分享图生成任务")
    @GetMapping("/tasks/{taskId}")
    public R<AiProfileCardTaskRespDTO> task(Authentication authentication,
                                           @PathVariable String taskId) {
        return R.ok(aiProfileCardService.task(currentUserId(authentication), taskId));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException(AiResumeErrorCode.AUTH_REQUIRED, "未登录或登录态失效");
        }
        return userId;
    }
}
