package com.kaipai.module.company.controller;

import com.kaipai.module.company.service.CompanyProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "公司/剧组档案管理")
@RestController
@RequestMapping("/company/profile")
@RequiredArgsConstructor
public class CompanyProfileController {

    private final CompanyProfileService companyProfileService;
}
