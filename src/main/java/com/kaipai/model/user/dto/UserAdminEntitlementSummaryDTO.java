package com.kaipai.model.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserAdminEntitlementSummaryDTO {

    private Integer latestStatus;
    private Integer totalCount;
    private Integer activeCount;
    private LocalDateTime latestExpireTime;
    private List<String> activeGrantCodes;
}
