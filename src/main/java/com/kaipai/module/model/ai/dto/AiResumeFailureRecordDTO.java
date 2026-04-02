package com.kaipai.module.model.ai.dto;

import lombok.Data;

@Data
public class AiResumeFailureRecordDTO {

    private String failureId;

    private Long userId;

    private String requestId;

    private String conversationId;

    private String instruction;

    private Integer errorCode;

    private String errorMessage;

    private String failureType;

    private String hitKeyword;

    private String createdAt;
}
