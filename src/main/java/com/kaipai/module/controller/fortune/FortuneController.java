package com.kaipai.module.controller.fortune;

import com.kaipai.module.server.fortune.service.FortuneReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "命理报告")
@RestController
@RequestMapping("/fortune")
@RequiredArgsConstructor
public class FortuneController {

    private final FortuneReportService fortuneReportService;
}
