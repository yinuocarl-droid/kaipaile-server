package com.kaipai.module.controller.membership;

import com.kaipai.module.server.membership.service.MembershipAccountService;
import com.kaipai.module.server.membership.service.MembershipChangeLogService;
import com.kaipai.module.server.membership.service.MembershipProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "会员中心")
@RestController
@RequestMapping("/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipProductService membershipProductService;
    private final MembershipAccountService membershipAccountService;
    private final MembershipChangeLogService membershipChangeLogService;
}
