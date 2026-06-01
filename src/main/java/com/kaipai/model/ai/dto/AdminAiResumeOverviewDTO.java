package com.kaipai.model.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminAiResumeOverviewDTO {

    private long totalHistoryCount;

    private long appliedHistoryCount;

    private long rolledBackHistoryCount;

    private int historyUserCount;

    private long currentMonthHistoryCount;

    private int currentMonthQuotaUserCount;

    private long currentMonthQuotaUsageTotal;

    private List<AdminAiResumeQuotaUserDTO> topQuotaUsers = new ArrayList<>();

    private List<AdminAiResumeHistoryItemDTO> recentHistories = new ArrayList<>();
}
