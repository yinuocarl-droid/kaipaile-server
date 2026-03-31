package com.kaipai.module.model.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminDashboardOverviewQueryDTO {

    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private String bizLine;
}
