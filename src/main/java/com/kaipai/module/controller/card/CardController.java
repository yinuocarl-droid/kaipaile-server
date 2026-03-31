package com.kaipai.module.controller.card;

import com.kaipai.module.server.card.service.ActorCardConfigService;
import com.kaipai.module.server.card.service.ActorSharePreferenceService;
import com.kaipai.module.server.card.service.CardSceneTemplateService;
import com.kaipai.module.server.card.service.TemplatePublishLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "场景模板配置")
@RestController
@RequestMapping("/card")
@RequiredArgsConstructor
public class CardController {

    private final CardSceneTemplateService templateService;
    private final ActorCardConfigService cardConfigService;
    private final ActorSharePreferenceService sharePreferenceService;
    private final TemplatePublishLogService publishLogService;
}
