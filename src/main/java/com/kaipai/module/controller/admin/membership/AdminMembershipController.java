package com.kaipai.module.controller.admin.membership;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.membership.dto.AdminMembershipAccountDetailDTO;
import com.kaipai.module.model.membership.dto.AdminMembershipAccountItemDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountCloseDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountExtendDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountOpenDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountQueryDTO;
import com.kaipai.module.model.membership.dto.MembershipChangeLogItemDTO;
import com.kaipai.module.model.membership.dto.MembershipChangeLogQueryDTO;
import com.kaipai.module.model.membership.dto.MembershipProductCreateDTO;
import com.kaipai.module.model.membership.dto.MembershipProductQueryDTO;
import com.kaipai.module.model.membership.entity.MembershipProduct;
import com.kaipai.module.server.membership.service.MembershipAccountService;
import com.kaipai.module.server.membership.service.MembershipChangeLogService;
import com.kaipai.module.server.membership.service.MembershipProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台会员")
@RestController
@RequestMapping("/admin/membership")
@RequiredArgsConstructor
public class AdminMembershipController {

    private final MembershipProductService membershipProductService;
    private final MembershipAccountService membershipAccountService;
    private final MembershipChangeLogService membershipChangeLogService;

    @Operation(summary = "会员商品列表")
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('page.membership.products')")
    public R<PageResult<MembershipProduct>> products(@Valid MembershipProductQueryDTO query) {
        return R.ok(membershipProductService.adminProductList(query));
    }

    @Operation(summary = "新建会员商品")
    @PostMapping("/products")
    @PreAuthorize("hasAuthority('action.membership.product.create')")
    public R<Void> createProduct(@Valid @RequestBody MembershipProductCreateDTO dto) {
        membershipProductService.createProduct(dto);
        return R.ok();
    }

    @Operation(summary = "会员账户列表")
    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('page.membership.accounts')")
    public R<PageResult<AdminMembershipAccountItemDTO>> accounts(@Valid MembershipAccountQueryDTO query) {
        return R.ok(membershipAccountService.adminAccountList(query));
    }

    @Operation(summary = "会员账户详情")
    @GetMapping("/accounts/{userId}")
    @PreAuthorize("hasAuthority('page.membership.accounts')")
    public R<AdminMembershipAccountDetailDTO> accountDetail(@PathVariable Long userId) {
        return R.ok(membershipAccountService.adminAccountDetail(userId));
    }

    @Operation(summary = "会员变更日志")
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('page.membership.logs')")
    public R<PageResult<MembershipChangeLogItemDTO>> logs(@Valid MembershipChangeLogQueryDTO query) {
        return R.ok(membershipChangeLogService.adminLogList(query));
    }

    @Operation(summary = "手工开通会员")
    @PostMapping("/accounts/{userId}/open")
    @PreAuthorize("hasAuthority('action.membership.account.open')")
    public R<Void> openAccount(@PathVariable Long userId, @Valid @RequestBody MembershipAccountOpenDTO dto) {
        membershipAccountService.openAccount(userId, dto);
        return R.ok();
    }

    @Operation(summary = "手工延期会员")
    @PostMapping("/accounts/{userId}/extend")
    @PreAuthorize("hasAuthority('action.membership.account.extend')")
    public R<Void> extendAccount(@PathVariable Long userId, @Valid @RequestBody MembershipAccountExtendDTO dto) {
        membershipAccountService.extendAccount(userId, dto);
        return R.ok();
    }

    @Operation(summary = "手工关闭会员")
    @PostMapping("/accounts/{userId}/close")
    @PreAuthorize("hasAuthority('action.membership.account.close')")
    public R<Void> closeAccount(@PathVariable Long userId, @Valid @RequestBody MembershipAccountCloseDTO dto) {
        membershipAccountService.closeAccount(userId, dto);
        return R.ok();
    }
}
