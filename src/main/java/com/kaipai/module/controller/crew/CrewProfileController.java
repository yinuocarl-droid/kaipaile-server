package com.kaipai.module.controller.crew;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.crew.dto.CrewProfileRespDTO;
import com.kaipai.module.model.crew.dto.CrewProfileSaveDTO;
import com.kaipai.module.server.crew.service.CrewProfileService;
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

@Tag(name = "团队/剧组档案管理")
@RestController
@RequestMapping("/crew")
@RequiredArgsConstructor
public class CrewProfileController {

    private final CrewProfileService crewProfileService;

    @Operation(summary = "获取我的剧组档案")
    @GetMapping("/mine")
    public R<CrewProfileRespDTO> mine(Authentication authentication) {
        return R.ok(crewProfileService.mineProfile(currentUserId(authentication)));
    }

    @Operation(summary = "获取剧组档案")
    @GetMapping("/{userId}")
    public R<CrewProfileRespDTO> profile(@PathVariable Long userId) {
        return R.ok(crewProfileService.profile(userId));
    }

    @Operation(summary = "保存剧组档案")
    @PutMapping
    public R<Void> save(Authentication authentication, @Valid @RequestBody CrewProfileSaveDTO dto) {
        crewProfileService.saveProfile(currentUserId(authentication), dto);
        return R.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
