package com.kaipai.controller.admin.content;

import com.kaipai.common.result.R;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernancePageDTO;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernanceQueryDTO;
import com.kaipai.service.actor.AdminActorCardGovernanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 00-265: 「分享内容」页的 v2 演员卡治理列表，复用分享内容页权限。 */
@Tag(name = "后台演员卡治理")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/admin/content/actor-cards")
public class AdminActorCardGovernanceController {

    private final AdminActorCardGovernanceService governanceService;

    @Operation(summary = "演员卡治理列表（actor_card，含已发布与草稿）")
    @GetMapping
    @PreAuthorize("hasAuthority('page.content.share-cards')")
    public R<AdminActorCardGovernancePageDTO> list(@Valid AdminActorCardGovernanceQueryDTO query) {
        return R.ok(governanceService.list(query));
    }
}
