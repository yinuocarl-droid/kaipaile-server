package com.kaipai.model.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiResumePolishReqDTO {

    private String instruction;

    private String conversationId;

    private String profileVersion;

    private ContextDTO context = new ContextDTO();

    private List<HistoryMessageDTO> history = new ArrayList<>();

    @Data
    public static class ContextDTO {

        private Long profileUserId;

        private String certificationStatus;

        private String levelLabel;

        private String name;

        private String gender;

        private Integer age;

        private Integer height;

        private String city;

        private String bodyType;

        private String hairStyle;

        private List<String> languages = new ArrayList<>();

        private List<String> skillTypes = new ArrayList<>();

        private List<EditableFieldDTO> editableFields = new ArrayList<>();
    }

    @Data
    public static class EditableFieldDTO {

        private String fieldType;

        private String fieldKey;

        private String label;

        private String targetId;

        private String projectName;

        private String roleName;

        private String shootDate;

        private String currentValue;
    }

    @Data
    public static class HistoryMessageDTO {

        private String role;

        private String content;
    }
}


