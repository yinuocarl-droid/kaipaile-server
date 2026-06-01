package com.kaipai.controller.api.actor;

import com.kaipai.service.actor.ActorExperienceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "演员演艺经历管理")
@RestController
@RequestMapping("/actor/experience")
@RequiredArgsConstructor
public class ActorExperienceController {

    private final ActorExperienceService actorExperienceService;
}
