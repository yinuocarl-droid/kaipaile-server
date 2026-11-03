package com.kaipai.controller.api.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.CapabilitiesResp;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.Resp;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.SaveReq;
import com.kaipai.service.actor.ActorCardExpandImageService;
import com.kaipai.service.actor.ActorCardVisualInputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only B5 endpoints for stable person/background inputs and asynchronous generation. */
@Tag(name = "演员卡 - AI首图输入")
@RestController
@RequestMapping("/actor-card/draft/{cardId}/visual-input")
@RequiredArgsConstructor
public class ActorCardVisualInputController {

    private final ActorCardVisualInputService service;
    private final ActorCardExpandImageService expandService;

    @Operation(summary = "读取当前AI首图输入；旧卡未配置时data为null")
    @GetMapping
    public R<Resp> get(Authentication authentication, @PathVariable Long cardId) {
        return R.ok(service.get(currentUserId(authentication), cardId));
    }

    @Operation(summary = "读取当前Provider对单人物/双参考图的真实请求能力")
    @GetMapping("/capabilities")
    public R<CapabilitiesResp> capabilities(Authentication authentication, @PathVariable Long cardId) {
        Long userId = currentUserId(authentication);
        service.get(userId, cardId);
        return R.ok(service.capabilities());
    }

    @Operation(summary = "保存稳定人物/背景输入并提交异步AI首图任务")
    @PutMapping
    public R<Resp> save(
            Authentication authentication,
            @PathVariable Long cardId,
            @Valid @RequestBody SaveReq request) {
        Long userId = currentUserId(authentication);
        service.save(userId, cardId, request);
        expandService.submitVisualInput(userId, cardId);
        return R.ok(service.get(userId, cardId));
    }

    @Operation(summary = "重试当前指纹对应的失败AI首图任务")
    @PostMapping("/retry")
    public R<Resp> retry(Authentication authentication, @PathVariable Long cardId) {
        Long userId = currentUserId(authentication);
        expandService.retryVisualInput(userId, cardId);
        return R.ok(service.get(userId, cardId));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
