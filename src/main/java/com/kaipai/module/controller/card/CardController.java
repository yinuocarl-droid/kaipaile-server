package com.kaipai.module.controller.card;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.module.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.module.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.module.server.card.service.ActorCardConfigService;
import com.kaipai.module.server.card.service.ActorSharePreferenceService;
import com.kaipai.module.server.card.service.CardSceneTemplateService;
import com.kaipai.module.server.card.service.TemplatePublishLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "场景模板配置")
@RestController
@RequestMapping("/card")
@RequiredArgsConstructor
public class CardController {

    private final CardSceneTemplateService templateService;
    private final ActorCardConfigService cardConfigService;
    private final ActorSharePreferenceService sharePreferenceService;
    private final TemplatePublishLogService publishLogService;

    @Operation(summary = "获取演员端场景模板列表")
    @GetMapping("/scene-templates")
    public R<List<ActorSceneTemplateRespDTO>> sceneTemplates() {
        return R.ok(templateService.actorSceneTemplates());
    }

    @Operation(summary = "获取演员名片配置")
    @GetMapping("/config")
    public R<ActorCardConfigRespDTO> config(@RequestParam Long actorId,
                                            @RequestParam("scene") String sceneKey) {
        return R.ok(cardConfigService.actorConfig(actorId, sceneKey));
    }

    @Operation(summary = "保存演员名片配置")
    @PostMapping("/config")
    public R<ActorCardConfigRespDTO> saveConfig(Authentication authentication,
                                                @Valid @RequestBody ActorCardConfigSaveDTO dto) {
        return R.ok(cardConfigService.saveActorConfig(currentUserId(authentication), dto));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
