package com.kaipai.module.model.ai.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminAiResumeFailureItemDTO {

    private String failureId;

    private Long userId;

    private String userName;

    private String phone;

    private Integer realAuthStatus;

    private Integer level;

    private String membershipTier;

    private String requestId;

    private String conversationId;

    private String instruction;

    private Integer errorCode;

    private String errorMessage;

    private String failureType;

    private String hitKeyword;

    private String handlingStatus;

    private String handlingNote;

    private Long handledByAdminId;

    private String handledByAdminName;

    private String handledAt;

    private String createdAt;

    private List<AiResumeFailureHandlingNoteDTO> handlingNotes;
}
