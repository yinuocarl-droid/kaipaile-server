package com.kaipai.service.actor;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.dto.ActorProfileDTO;
import com.kaipai.model.actor.dto.ActorProfileSaveDTO;
import com.kaipai.model.actor.dto.ActorSearchQueryDTO;
import com.kaipai.model.actor.entity.ActorProfile;

public interface ActorProfileService extends IService<ActorProfile> {

    ActorProfileDTO mine(Long currentUserId);

    ActorProfileDTO profile(Long userId);

    ActorProfileDTO detail(Long userId, boolean includeContact);

    void saveProfile(Long currentUserId, ActorProfileSaveDTO dto);

    PageResult<ActorProfileDTO> search(ActorSearchQueryDTO query);
}
