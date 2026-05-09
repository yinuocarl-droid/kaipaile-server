package com.kaipai.module.model.capability.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AdminCapabilityBenefitOverviewDTO {

    private List<BenefitItem> benefitItems;
    private List<BenefitCapabilityItem> benefitCapabilityItems;

    @Data
    public static class BenefitItem {
        private String benefitId;
        private Long productId;
        private String productCode;
        private String productName;
        private String benefitCode;
        private String benefitName;
        private Integer capabilityTier;
        private String capabilitySummary;
        private Integer status;
        private LocalDateTime lastUpdate;
        private List<String> affectedPages;
        private List<String> artifactTypes;
    }

    @Data
    public static class BenefitCapabilityItem {
        private String benefitCode;
        private String benefitName;
        private String capabilitySummary;
        private Map<Integer, Boolean> tierEnabledMap;
        private List<Long> relatedProductIds;
        private List<String> relatedProductCodes;
        private List<String> affectedPages;
        private List<String> artifactTypes;
    }
}
