package com.kaipai.module.controller.ai;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.common.result.ResultCode;
import com.kaipai.module.model.ai.dto.AiResumeNotificationDispatchResultDTO;
import com.kaipai.module.model.ai.dto.AiResumeNotificationReceiptCallbackDTO;
import com.kaipai.module.server.ai.config.AiResumeNotificationProperties;
import com.kaipai.module.server.ai.service.AiResumeNotificationDispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 简历通知回执")
@RestController
@RequestMapping("/internal/ai/resume/notification-receipts")
@RequiredArgsConstructor
public class AiResumeNotificationReceiptController {

    private final AiResumeNotificationDispatchService aiResumeNotificationDispatchService;
    private final AiResumeNotificationProperties properties;

    @Operation(summary = "AI 简历通知供应商回执")
    @PostMapping("/provider")
    public R<AiResumeNotificationDispatchResultDTO> providerReceipt(
            @RequestBody(required = false) AiResumeNotificationReceiptCallbackDTO callback,
            HttpServletRequest request) {
        ensureCallbackAuthorized(request);
        return R.ok(aiResumeNotificationDispatchService.ingestReceipt(
                callback == null ? new AiResumeNotificationReceiptCallbackDTO() : callback
        ));
    }

    private void ensureCallbackAuthorized(HttpServletRequest request) {
        String expectedToken = properties.getCallbackToken();
        String headerName = properties.getCallbackHeader();
        if (!StringUtils.hasText(expectedToken) || !StringUtils.hasText(headerName)) {
            throw new BizException("AI 通知回执令牌未配置");
        }
        String actualToken = request == null ? null : request.getHeader(headerName.trim());
        if (!expectedToken.trim().equals(actualToken == null ? null : actualToken.trim())) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "AI 通知回执令牌无效");
        }
    }
}
