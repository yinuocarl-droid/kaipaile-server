package com.kaipai.module.model.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiResumeRollbackRespDTO {

    private String historyId;

    private String rollbackId;

    private List<AiResumeHistoryItemDTO.FieldSnapshotDTO> restoredSnapshots = new ArrayList<>();

    private String profileVersion;

    private String rolledBackAt;
}
