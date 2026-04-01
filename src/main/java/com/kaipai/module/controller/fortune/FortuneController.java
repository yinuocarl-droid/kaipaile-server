package com.kaipai.module.controller.fortune;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.fortune.dto.FortuneReportRespDTO;
import com.kaipai.module.server.fortune.service.FortuneReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "命理报告")
@RestController
@RequestMapping("/fortune")
@RequiredArgsConstructor
public class FortuneController {

    private final FortuneReportService fortuneReportService;

    @Operation(summary = "获取当前用户命理报告")
    @GetMapping("/report")
    public R<FortuneReportRespDTO> report(Authentication authentication) {
        return R.ok(fortuneReportService.currentReport(currentUserId(authentication)));
    }

    @Operation(summary = "应用幸运色到当前场景名片")
    @PostMapping("/apply-lucky-color")
    public R<Void> applyLuckyColor(Authentication authentication, @RequestBody Map<String, String> request) {
        fortuneReportService.applyLuckyColor(currentUserId(authentication), request == null ? null : request.get("sceneKey"));
        return R.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
