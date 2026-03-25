package com.kaipai.module.controller.recruit;

import com.kaipai.module.server.recruit.service.RecruitPostService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "招募帖子管理")
@RestController
@RequestMapping("/recruit/post")
@RequiredArgsConstructor
public class RecruitPostController {

    private final RecruitPostService recruitPostService;
}
