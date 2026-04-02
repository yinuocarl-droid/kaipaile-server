package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiResumeHistoryItemDTO {

    private String historyId;

    private String draftId;

    private String requestId;

    private String conversationId;

    private String instruction;

    private String reply;

    private String status;

    private List<AiResumePolishRespDTO.PatchDTO> patches = new ArrayList<>();

    private List<FieldSnapshotDTO> beforeSnapshot = new ArrayList<>();

    private List<FieldSnapshotDTO> afterSnapshot = new ArrayList<>();

    private String createdAt;

    private String appliedAt;

    private String rolledBackAt;

    @Data
    public static class FieldSnapshotDTO {

        private String fieldKey;

        private String value;
    }
}
