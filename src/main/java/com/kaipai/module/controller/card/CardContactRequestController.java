package com.kaipai.module.controller.card;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.card.dto.ContactRequestApplyDTO;
import com.kaipai.module.model.card.dto.ContactRequestDecisionDTO;
import com.kaipai.module.model.card.dto.ContactRequestItemDTO;
import com.kaipai.module.model.card.dto.ContactRequestStatusRespDTO;
import com.kaipai.module.model.card.dto.ContactRequestStatusQueryDTO;
import com.kaipai.module.server.card.service.ShareCardContactRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分享卡联系方式授权")
@RestController
@RequestMapping("/card/contact-requests")
@RequiredArgsConstructor
public class CardContactRequestController {

    private final ShareCardContactRequestService contactRequestService;

    @Operation(summary = "发起联系方式申请")
    @PostMapping
    public R<ContactRequestStatusRespDTO> apply(Authentication authentication, @Valid @RequestBody ContactRequestApplyDTO dto) {
        return R.ok(contactRequestService.apply(currentUserId(authentication), dto));
    }

    @Operation(summary = "查询我对某张卡的联系方式申请状态")
    @GetMapping("/status")
    public R<ContactRequestStatusRespDTO> status(Authentication authentication,
                                                 @Valid @ModelAttribute ContactRequestStatusQueryDTO query) {
        return R.ok(contactRequestService.status(currentUserId(authentication), query.getShareCardId()));
    }

    @Operation(summary = "查询我已获批的联系方式列表")
    @GetMapping("/approved")
    public R<List<ContactRequestItemDTO>> approved(Authentication authentication) {
        return R.ok(contactRequestService.approvedContacts(currentUserId(authentication)));
    }

    @Operation(summary = "查询我收到的联系方式申请")
    @GetMapping("/owned")
    public R<List<ContactRequestItemDTO>> owned(Authentication authentication,
                                                @RequestParam(required = false) String status) {
        return R.ok(contactRequestService.ownedRequests(currentUserId(authentication), status));
    }

    @Operation(summary = "同意联系方式申请")
    @PostMapping("/{requestId}/approve")
    public R<ContactRequestStatusRespDTO> approve(Authentication authentication,
                                                  @PathVariable Long requestId,
                                                  @RequestBody(required = false) ContactRequestDecisionDTO dto) {
        return R.ok(contactRequestService.approve(currentUserId(authentication), requestId, dto));
    }

    @Operation(summary = "拒绝联系方式申请")
    @PostMapping("/{requestId}/reject")
    public R<ContactRequestStatusRespDTO> reject(Authentication authentication,
                                                 @PathVariable Long requestId,
                                                 @RequestBody(required = false) ContactRequestDecisionDTO dto) {
        return R.ok(contactRequestService.reject(currentUserId(authentication), requestId, dto));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
