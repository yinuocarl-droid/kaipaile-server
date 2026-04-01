package com.kaipai.module.controller.actor;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.dto.ActorSearchQueryDTO;
import com.kaipai.module.server.actor.service.ActorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "演员展示")
@RestController
@RequestMapping("/actor")
@RequiredArgsConstructor
public class ActorController {

    private final ActorProfileService actorProfileService;

    @Operation(summary = "演员公开详情")
    @GetMapping("/{userId}")
    public R<ActorProfileDTO> detail(Authentication authentication, @PathVariable Long userId) {
        return R.ok(actorProfileService.detail(userId, authentication != null && authentication.getPrincipal() != null));
    }

    @Operation(summary = "演员搜索")
    @GetMapping("/search")
    public R<PageResult<ActorProfileDTO>> search(ActorSearchQueryDTO query) {
        return R.ok(actorProfileService.search(query));
    }
}
