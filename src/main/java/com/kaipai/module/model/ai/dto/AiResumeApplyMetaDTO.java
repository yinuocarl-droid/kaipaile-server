package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiResumeApplyMetaDTO {

    private String draftId;

    private String requestId;

    private List<String> appliedPatchIds = new ArrayList<>();

    private String profileVersion;
}
