package com.kaipai.module.server.system.service;

import com.kaipai.module.model.system.dto.AdminDashboardOverviewDTO;
import com.kaipai.module.model.system.dto.AdminDashboardOverviewQueryDTO;

public interface AdminDashboardService {

    AdminDashboardOverviewDTO overview(AdminDashboardOverviewQueryDTO query);
}
