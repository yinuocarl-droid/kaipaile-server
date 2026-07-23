package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.actor.ActorProfileRepresentativeWorkMapper;
import com.kaipai.model.actor.dto.ActorRepresentativeWorksUpdateDTO;
import com.kaipai.model.actor.dto.ActorWorkQueryDTO;
import com.kaipai.model.actor.dto.ActorWorkSaveDTO;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.actor.entity.ActorProfile;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActorWorkServiceImplTest {

    private ActorExperienceMapper experienceMapper;
    private ActorProfileMapper profileMapper;
    private ActorProfileRepresentativeWorkMapper representativeMapper;
    private com.kaipai.mapper.card.ShareCardWorkMapper shareCardWorkMapper;
    private ActorWorkServiceImpl service;

    @BeforeEach
    void setUp() {
        experienceMapper = org.mockito.Mockito.mock(ActorExperienceMapper.class);
        profileMapper = org.mockito.Mockito.mock(ActorProfileMapper.class);
        representativeMapper = org.mockito.Mockito.mock(ActorProfileRepresentativeWorkMapper.class);
        shareCardWorkMapper = org.mockito.Mockito.mock(com.kaipai.mapper.card.ShareCardWorkMapper.class);
        service = new ActorWorkServiceImpl(experienceMapper, profileMapper, representativeMapper, shareCardWorkMapper, new com.fasterxml.jackson.databind.ObjectMapper());

        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(11L);
        profile.setUserId(7L);
        profile.setWorkLibraryVersion(4L);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(profileMapper.incrementWorkLibraryVersion(11L)).thenReturn(1);
    }

    @Test
    void listWorksDefaultsToTenItemsAndPreservesTotal() {
        Page<ActorExperience> page = new Page<>(1, 10, 29);
        page.setRecords(java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(this::workEntity)
                .toList());
        when(experienceMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.listWorks(7L, new ActorWorkQueryDTO());

        assertEquals(10, result.getList().size());
        assertEquals(29, result.getTotal());
    }

    @Test
    void createWorkRejectsDuplicateBeforeInsert() {
        when(experienceMapper.selectCount(any())).thenReturn(1L);

        BizException error = assertThrows(BizException.class,
                () -> service.createWork(7L, workSave("《绝不回头，白爷宠她成瘾》", "程雪")));

        assertEquals(46015, error.getCode());
        verify(experienceMapper, never()).insert(any());
    }

    @Test
    void representativeWorksRejectMoreThanSix() {
        ActorRepresentativeWorksUpdateDTO request = new ActorRepresentativeWorksUpdateDTO();
        request.setExperienceIds(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));

        assertThrows(BizException.class, () -> service.replaceRepresentativeWorks(7L, request));
        verify(representativeMapper, never()).insert(any());
    }

    private ActorWorkSaveDTO workSave(String projectName, String roleName) {
        ActorWorkSaveDTO dto = new ActorWorkSaveDTO();
        dto.setProjectName(projectName);
        dto.setRoleName(roleName);
        return dto;
    }

    private ActorExperience workEntity(long id) {
        ActorExperience work = new ActorExperience();
        work.setExperienceId(id);
        work.setDramaName("作品" + id);
        work.setRoleName("角色" + id);
        return work;
    }
}
