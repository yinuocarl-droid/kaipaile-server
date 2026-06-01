package com.kaipai.model.ai.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminAiResumeFailureAssigneeOptionDTO {

    private Long adminUserId;

    private String userName;

    private String account;

    private List<String> roleCodes;

    private List<String> roleNames;
}
