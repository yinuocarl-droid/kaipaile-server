package com.kaipai.common.auth;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AdminOperationLogCommand {

    String moduleCode;
    String operationCode;
    String targetType;
    Long targetId;
    Object beforeSnapshot;
    Object afterSnapshot;
    Object extraContext;
    Integer operationResult;
    String failReason;
    String confirmToken;
    LocalDateTime confirmedAt;
}
