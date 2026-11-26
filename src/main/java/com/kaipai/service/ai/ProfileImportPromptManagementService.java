package com.kaipai.service.ai;

import com.kaipai.model.ai.dto.ProfileImportPromptAuditRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptCreateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptRestoreReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptTemplateSummaryRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptTestResultRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptUpdateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionActionReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionDetailRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionSummaryRespDTO;
import java.util.List;

public interface ProfileImportPromptManagementService {

    List<ProfileImportPromptTemplateSummaryRespDTO> templates();

    List<ProfileImportPromptVersionSummaryRespDTO> versions(String templateCode);

    ProfileImportPromptVersionDetailRespDTO version(Long promptVersionId);

    ProfileImportPromptTemplateSummaryRespDTO createDraft(
            Long operatorId,
            String templateCode,
            ProfileImportPromptCreateDraftReqDTO request);

    ProfileImportPromptVersionDetailRespDTO updateDraft(
            Long operatorId,
            Long promptVersionId,
            ProfileImportPromptUpdateDraftReqDTO request);

    ProfileImportPromptTemplateSummaryRespDTO abandonDraft(
            Long operatorId,
            Long promptVersionId,
            ProfileImportPromptVersionActionReqDTO request);

    ProfileImportPromptTestResultRespDTO test(Long operatorId, Long promptVersionId);

    ProfileImportPromptTemplateSummaryRespDTO publish(
            Long operatorId,
            Long promptVersionId,
            ProfileImportPromptVersionActionReqDTO request);

    ProfileImportPromptTemplateSummaryRespDTO restore(
            Long operatorId,
            String templateCode,
            Long targetVersionId,
            ProfileImportPromptRestoreReqDTO request);

    List<ProfileImportPromptAuditRespDTO> audits();
}
