package com.kaipai.model.actor.card.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/** 00-265 后台演员卡治理分页结果，附带不受 status 与分页影响的全局状态计数。 */
@Data
public class AdminActorCardGovernancePageDTO {
    private long total;
    private List<AdminActorCardGovernanceItemDTO> list = Collections.emptyList();
    private StatusCounts statusCounts = new StatusCounts();

    @Data
    public static class StatusCounts {
        private long total;
        private long published;
        private long draft;
    }
}
