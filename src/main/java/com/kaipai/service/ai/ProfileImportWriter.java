package com.kaipai.service.ai; import com.kaipai.model.ai.dto.ProfileImportApplyReqDTO; public interface ProfileImportWriter { String applyImport(Long userId, ProfileImportApplyReqDTO request); }
