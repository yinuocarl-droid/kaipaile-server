package com.kaipai.module.model.ai.dto;

import lombok.Data;

@Data
public class AiResumeFailureHandlingNoteDTO {

    private String handlingStatus;

    private String handlingNote;

    private Long handledByAdminId;

    private String handledByAdminName;

    private String handledAt;
}
