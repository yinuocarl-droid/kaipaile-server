package com.kaipai.model.capability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CapabilityBenefitSaveDTO {

    @NotNull
    private Long productId;

    @NotBlank
    private String benefitCode;

    @NotBlank
    private String benefitName;

    private String capabilitySummary;

    @NotNull
    private Integer status;

    private List<String> affectedPages;

    private List<String> artifactTypes;
}
