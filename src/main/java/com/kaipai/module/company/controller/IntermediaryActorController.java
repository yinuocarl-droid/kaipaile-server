package com.kaipai.module.company.controller;

import com.kaipai.module.company.service.IntermediaryActorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "中介演员授权管理")
@RestController
@RequestMapping("/intermediary/actor")
@RequiredArgsConstructor
public class IntermediaryActorController {

    private final IntermediaryActorService intermediaryActorService;
}
