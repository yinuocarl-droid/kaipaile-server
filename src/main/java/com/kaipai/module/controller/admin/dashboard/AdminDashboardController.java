package com.kaipai.module.controller.admin.dashboard;

import com.kaipai.common.result.R;
import com.kaipai.module.model.system.dto.AdminDashboardOverviewDTO;
import com.kaipai.module.model.system.dto.AdminDashboardOverviewQueryDTO;
import com.kaipai.module.server.system.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台工作台")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "工作台概览")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('page.dashboard.index')")
    public R<AdminDashboardOverviewDTO> overview(AdminDashboardOverviewQueryDTO query) {
        return R.ok(adminDashboardService.overview(query));
    }
}
