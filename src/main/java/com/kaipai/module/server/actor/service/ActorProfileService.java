package com.kaipai.module.server.actor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.dto.ActorProfileDTO;
import com.kaipai.module.model.actor.dto.ActorProfileSaveDTO;
import com.kaipai.module.model.actor.dto.ActorSearchQueryDTO;
import com.kaipai.module.model.actor.entity.ActorProfile;

public interface ActorProfileService extends IService<ActorProfile> {

    ActorProfileDTO mine(Long currentUserId);

    ActorProfileDTO profile(Long userId);

    ActorProfileDTO detail(Long userId, boolean includeContact);

    void saveProfile(Long currentUserId, ActorProfileSaveDTO dto);

    PageResult<ActorProfileDTO> search(ActorSearchQueryDTO query);
}
