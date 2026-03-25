package com.kaipai.module.controller.actor;

import com.kaipai.module.server.actor.service.ActorProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "演员档案管理")
@RestController
@RequestMapping("/actor/profile")
@RequiredArgsConstructor
public class ActorProfileController {

    private final ActorProfileService actorProfileService;
}
