package com.kaipai.module.controller.recruit;

import com.kaipai.module.server.recruit.service.RecruitApplyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "演员投递管理")
@RestController
@RequestMapping("/recruit/apply")
@RequiredArgsConstructor
public class RecruitApplyController {

    private final RecruitApplyService recruitApplyService;
}
