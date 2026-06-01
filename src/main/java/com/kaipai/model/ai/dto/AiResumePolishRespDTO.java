package com.kaipai.model.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiResumePolishRespDTO {

    private String requestId;

    private String conversationId;

    private String draftId;

    private String reply;

    private List<PatchDTO> patches = new ArrayList<>();

    private ActorAiQuotaRespDTO quota;

    private List<String> warnings = new ArrayList<>();

    @Data
    public static class PatchDTO {

        private String patchId;

        private String fieldType;

        private String fieldKey;

        private String label;

        private String targetId;

        private String beforeValue;

        private String afterValue;

        private String reason;

        private String status;
    }
}
