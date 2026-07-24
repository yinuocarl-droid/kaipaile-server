package com.kaipai.controller.api.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.dto.ActorProfileDTO;
import com.kaipai.model.actor.dto.ActorProfileSaveDTO;
import com.kaipai.model.actor.dto.ActorProfileMineUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileRespDTO;
import com.kaipai.service.actor.ActorProfileService;
import com.kaipai.service.actor.ActorProfileWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "演员档案管理")
@RestController
@RequestMapping("/actor/profile")
@RequiredArgsConstructor
public class ActorProfileController {

    private final ActorProfileService actorProfileService;
    private final ActorProfileWriteService actorProfileWriteService;

    @Operation(summary = "获取我的演员档案")
    @GetMapping("/mine")
    public R<ActorProfileDTO> mine(Authentication authentication) {
        return R.ok(actorProfileService.mine(currentUserId(authentication)));
    }

    @Operation(summary = "获取我的核心与职业档案")
    @GetMapping("/mine/career")
    public R<ActorProfileRespDTO> careerMine(Authentication authentication) {
        return R.ok(actorProfileWriteService.mine(currentUserId(authentication)));
    }

    @Deprecated(forRemoval = false)
    @Operation(
            summary = "获取我的旧版聚合演员档案（兼容）",
            description = "已废弃；仅供旧聚合消费者过渡。新版调用 GET /api/actor/profile/mine/career。",
            deprecated = true)
    @GetMapping("/mine/legacy")
    public R<ActorProfileDTO> legacyMine(Authentication authentication) {
        return R.ok(actorProfileService.mine(currentUserId(authentication)));
    }

    @Operation(summary = "获取演员档案")
    @GetMapping("/{userId}")
    public R<ActorProfileDTO> profile(Authentication authentication, @PathVariable Long userId) {
        if (!currentUserId(authentication).equals(userId)) {
            throw new BizException("只能查看自己的演员档案");
        }
        return R.ok(actorProfileService.profile(userId));
    }

    @Operation(summary = "保存演员档案")
    @PutMapping
    public R<Void> save(Authentication authentication, @Valid @RequestBody ActorProfileSaveDTO dto) {
        actorProfileService.saveProfile(currentUserId(authentication), dto);
        return R.ok();
    }

    @Operation(summary = "保存我的核心与职业档案")
    @PutMapping("/mine")
    public R<ActorProfileRespDTO> saveMine(Authentication authentication,
                                           @Valid @RequestBody ActorProfileMineUpdateDTO dto) {
        return R.ok(actorProfileWriteService.saveMine(currentUserId(authentication), dto));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
