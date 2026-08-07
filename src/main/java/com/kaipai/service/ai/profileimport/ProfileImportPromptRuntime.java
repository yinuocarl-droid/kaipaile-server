package com.kaipai.service.ai.profileimport;

/** Rendered Prompt and immutable lineage used for one profile-import model call. */
public record ProfileImportPromptRuntime(
        Long templateId,
        String templateCode,
        String scene,
        Long promptVersionId,
        Integer versionNo,
        String schemaVersion,
        String contractVersion,
        String systemPrompt,
        String repairPrompt,
        String runtimeSha256) {

    @Override
    public String toString() {
        return "ProfileImportPromptRuntime[templateCode=" + templateCode
                + ", scene=" + scene
                + ", promptVersionId=" + promptVersionId
                + ", versionNo=" + versionNo
                + ", schemaVersion=" + schemaVersion
                + ", contractVersion=" + contractVersion
                + ", runtimeSha256=" + runtimeSha256 + "]";
    }
}
