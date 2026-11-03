package com.kaipai.controller.api.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.DecorationEnvelope;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.UpgradePreviewResp;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.UpgradeReq;
import com.kaipai.service.actor.ActorCardDecorationAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Owner-only decoration upgrade lifecycle; no endpoint starts or retries an AI task. */
@Tag(name = "演员卡 - 装修锁版升级")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/actor-card/{cardId}/decoration")
public class ActorCardDecorationAssignmentController {
    private final ActorCardDecorationAssignmentService service;

    @Operation(summary = "预览演员卡当前来源绑定的可升级装修版本")
    @GetMapping("/upgrade-preview")
    public R<UpgradePreviewResp> preview(Authentication authentication,
                                         @PathVariable @Positive Long cardId) {
        return R.ok(service.previewUpgrade(currentUserId(authentication), cardId));
    }

    @Operation(summary = "以当前锁版做CAS确认升级，不重新生成AI首图")
    @PostMapping("/upgrade")
    public R<DecorationEnvelope> upgrade(Authentication authentication,
                                         @PathVariable @Positive Long cardId,
                                         @Valid @RequestBody UpgradeReq request) {
        return R.ok(service.confirmUpgrade(currentUserId(authentication), cardId, request));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
