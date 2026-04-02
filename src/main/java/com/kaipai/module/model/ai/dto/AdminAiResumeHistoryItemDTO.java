package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminAiResumeHistoryItemDTO {

    private String historyId;

    private Long userId;

    private String userName;

    private String phone;

    private Integer realAuthStatus;

    private Integer level;

    private String membershipTier;

    private String draftId;

    private String requestId;

    private String conversationId;

    private String instruction;

    private String reply;

    private String status;

    private Integer patchCount;

    private List<AiResumePolishRespDTO.PatchDTO> patches = new ArrayList<>();

    private List<AiResumeHistoryItemDTO.FieldSnapshotDTO> beforeSnapshot = new ArrayList<>();

    private List<AiResumeHistoryItemDTO.FieldSnapshotDTO> afterSnapshot = new ArrayList<>();

    private String createdAt;

    private String appliedAt;

    private String rolledBackAt;
}
