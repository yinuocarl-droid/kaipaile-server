package com.kaipai.module.controller.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.dto.ActorProfileSaveDTO;
import com.kaipai.module.server.actor.service.ActorProfileService;
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

    @Operation(summary = "获取我的演员档案")
    @GetMapping("/mine")
    public R<ActorProfileDTO> mine(Authentication authentication) {
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

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
