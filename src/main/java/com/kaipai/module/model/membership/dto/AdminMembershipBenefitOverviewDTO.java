package com.kaipai.module.model.membership.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AdminMembershipBenefitOverviewDTO {

    private List<BenefitItem> benefitItems;
    private List<CapabilityMatrixItem> capabilityMatrix;

    @Data
    public static class BenefitItem {
        private Long productId;
        private String productCode;
        private String productName;
        private String benefitCode;
        private String benefitName;
        private Integer membershipTier;
        private String capabilitySummary;
        private Integer status;
        private LocalDateTime lastUpdate;
        private List<String> affectedPages;
        private List<String> artifactTypes;
    }

    @Data
    public static class CapabilityMatrixItem {
        private String capabilityCode;
        private String capabilityName;
        private String capabilitySummary;
        private Map<Integer, Boolean> tierEnabledMap;
        private List<Long> relatedProductIds;
        private List<String> relatedProductCodes;
        private List<String> affectedPages;
        private List<String> artifactTypes;
    }
}
