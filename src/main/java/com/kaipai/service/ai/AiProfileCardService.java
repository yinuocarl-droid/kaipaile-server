package com.kaipai.service.ai;

import com.kaipai.model.ai.dto.AiProfileCardGenerateReqDTO;
import com.kaipai.model.ai.dto.AiProfileCardGenerateRespDTO;
import com.kaipai.model.ai.dto.AiProfileCardArtifactRespDTO;
import com.kaipai.model.ai.dto.AiProfileCardTaskRespDTO;

import java.util.List;

public interface AiProfileCardService {

    AiProfileCardGenerateRespDTO generate(Long currentUserId, AiProfileCardGenerateReqDTO dto);

    AiProfileCardTaskRespDTO task(Long currentUserId, String taskId);

    List<AiProfileCardTaskRespDTO> tasks(Long currentUserId);

    List<AiProfileCardArtifactRespDTO> artifacts(Long currentUserId);

    AiProfileCardArtifactRespDTO artifact(String artifactId);

    AiProfileCardArtifactRespDTO latestArtifactByShareCard(Long shareCardId);

    void deleteArtifact(Long currentUserId, String artifactId);
}
