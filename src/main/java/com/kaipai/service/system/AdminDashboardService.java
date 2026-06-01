package com.kaipai.service.system;

import com.kaipai.model.system.dto.AdminDashboardOverviewDTO;
import com.kaipai.model.system.dto.AdminDashboardOverviewQueryDTO;

public interface AdminDashboardService {

    AdminDashboardOverviewDTO overview(AdminDashboardOverviewQueryDTO query);
}


