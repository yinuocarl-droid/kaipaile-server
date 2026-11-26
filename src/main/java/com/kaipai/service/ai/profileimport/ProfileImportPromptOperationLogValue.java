package com.kaipai.service.ai.profileimport;

/** Sanitized Prompt governance values allowed in the global admin operation log. */
public record ProfileImportPromptOperationLogValue(
        Long templateId,
        Long promptVersionId,
        Integer versionNo,
        String scene,
        String contentSha256,
        String runtimeSha256,
        String lifecycleStatus,
        String reasonCode,
        Integer candidateCount,
        Integer workCount) {
}
