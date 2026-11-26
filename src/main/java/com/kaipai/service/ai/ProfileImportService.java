package com.kaipai.service.ai; import com.kaipai.model.ai.dto.*; public interface ProfileImportService { ProfileImportExtractionRespDTO extract(Long userId,ProfileImportExtractReqDTO request); }
