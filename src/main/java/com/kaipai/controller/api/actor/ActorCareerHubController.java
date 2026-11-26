package com.kaipai.controller.api.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.dto.ActorCareerHubSummaryRespDTO;
import com.kaipai.service.actor.ActorCareerHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/actor/career-hub")
@RequiredArgsConstructor
public class ActorCareerHubController {
    private final ActorCareerHubService service;

    @GetMapping("/summary")
    public R<ActorCareerHubSummaryRespDTO> summary(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return R.ok(service.summary(userId));
    }
}
