package com.kaipai.module.controller.level;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.module.server.capability.service.CapabilityAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "等级信息")
@RestController
@RequestMapping("/level")
@RequiredArgsConstructor
public class LevelController {

    private final CapabilityAccountService capabilityAccountService;

    @Operation(summary = "获取当前用户等级信息")
    @GetMapping("/info")
    public R<ActorLevelInfoRespDTO> info(Authentication authentication) {
        return R.ok(capabilityAccountService.actorLevelInfo(currentUserId(authentication)));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
