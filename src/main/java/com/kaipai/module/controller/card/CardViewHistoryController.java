package com.kaipai.module.controller.card;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.card.dto.ShareCardHistoryItemDTO;
import com.kaipai.module.model.card.dto.ShareCardHistoryRecordDTO;
import com.kaipai.module.server.card.service.ShareCardViewHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分享卡查看历史")
@RestController
@RequestMapping("/card/view-histories")
@RequiredArgsConstructor
public class CardViewHistoryController {

    private final ShareCardViewHistoryService shareCardViewHistoryService;

    @Operation(summary = "记录查看历史")
    @PostMapping
    public R<Void> record(Authentication authentication, @Valid @RequestBody ShareCardHistoryRecordDTO dto) {
        shareCardViewHistoryService.record(currentUserId(authentication), dto);
        return R.ok();
    }

    @Operation(summary = "查询我的查看历史")
    @GetMapping
    public R<List<ShareCardHistoryItemDTO>> myHistory(Authentication authentication) {
        return R.ok(shareCardViewHistoryService.myHistory(currentUserId(authentication)));
    }

    @Operation(summary = "清空我的查看历史")
    @PostMapping("/clear")
    public R<Void> clear(Authentication authentication) {
        shareCardViewHistoryService.clear(currentUserId(authentication));
        return R.ok();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
