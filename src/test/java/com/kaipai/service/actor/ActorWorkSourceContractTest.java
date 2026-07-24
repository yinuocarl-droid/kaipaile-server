package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.actor.ActorProfileRepresentativeWorkMapper;
import com.kaipai.mapper.card.ShareCardWorkMapper;
import com.kaipai.model.actor.dto.ActorWorkQueryDTO;
import com.kaipai.model.actor.dto.ActorWorkRespDTO;
import com.kaipai.model.actor.dto.ActorWorkSaveDTO;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.service.actor.impl.ActorWorkServiceImpl;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class ActorWorkSourceContractTest {
    private static final Long USER_ID = 7L;
    private static final Long PROFILE_ID = 11L;
    private static final Long WORK_ID = 21L;

    private ActorExperienceMapper experienceMapper;
    private ActorProfileMapper profileMapper;
    private ActorWorkServiceImpl service;
    private ObjectMapper applicationObjectMapper;

    @BeforeEach
    void setUp() {
        experienceMapper = org.mockito.Mockito.mock(ActorExperienceMapper.class);
        profileMapper = org.mockito.Mockito.mock(ActorProfileMapper.class);
        ActorProfileRepresentativeWorkMapper representativeMapper =
                org.mockito.Mockito.mock(ActorProfileRepresentativeWorkMapper.class);
        ShareCardWorkMapper shareCardWorkMapper = org.mockito.Mockito.mock(ShareCardWorkMapper.class);
        applicationObjectMapper = Jackson2ObjectMapperBuilder.json().build();
        service = new ActorWorkServiceImpl(
                experienceMapper,
                profileMapper,
                representativeMapper,
                shareCardWorkMapper,
                applicationObjectMapper);

        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(PROFILE_ID);
        profile.setUserId(USER_ID);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(profileMapper.incrementWorkLibraryVersion(PROFILE_ID)).thenReturn(1);
        when(experienceMapper.insert(any())).thenReturn(1);
        when(experienceMapper.updateById(any())).thenReturn(1);
    }

    @Test
    void manualCreateSetsServerOwnedSourceAndResponseReturnsIt() {
        ActorWorkRespDTO result = service.createWork(USER_ID, work("测试作品", "角色"));

        assertEquals("manual", result.getSourceType());
        verify(experienceMapper).insert(argThat(item -> "manual".equals(item.getSourceType())));
    }

    @Test
    void saveDtoHasNoSourceTypeAndMaliciousJsonCannotOverrideManual() throws Exception {
        assertFalse(Arrays.stream(ActorWorkSaveDTO.class.getDeclaredFields())
                .anyMatch(field -> "sourceType".equals(field.getName())));

        ActorWorkSaveDTO payload = applicationObjectMapper.readValue(
                "{\"projectName\":\"测试作品\",\"sourceType\":\"migration\"}",
                ActorWorkSaveDTO.class);
        ActorWorkRespDTO created = service.createWork(USER_ID, payload);

        assertEquals("manual", created.getSourceType());
        verify(experienceMapper).insert(argThat(item -> "manual".equals(item.getSourceType())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"import", "migration"})
    void ordinaryUpdatePreservesStoredSource(String storedSource) {
        when(experienceMapper.selectOne(any())).thenReturn(workEntity(WORK_ID, storedSource));

        ActorWorkRespDTO updated = service.updateWork(
                USER_ID, WORK_ID, work("更新作品", "更新角色"));

        assertEquals(storedSource, updated.getSourceType());
        verify(experienceMapper).updateById(
                argThat(item -> storedSource.equals(item.getSourceType())));
    }

    @Test
    void listAndDetailResponsesExposeStoredSource() {
        Page<ActorExperience> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(workEntity(31L, "import")));
        when(experienceMapper.selectPage(any(), any())).thenReturn(page);
        when(experienceMapper.selectOne(any())).thenReturn(workEntity(32L, "migration"));

        assertEquals("import", service.listWorks(USER_ID, new ActorWorkQueryDTO())
                .getList().get(0).getSourceType());
        assertEquals("migration", service.work(USER_ID, 32L).getSourceType());
    }

    private ActorWorkSaveDTO work(String projectName, String roleName) {
        ActorWorkSaveDTO dto = new ActorWorkSaveDTO();
        dto.setProjectName(projectName);
        dto.setRoleName(roleName);
        return dto;
    }

    private ActorExperience workEntity(Long id, String sourceType) {
        ActorExperience work = new ActorExperience();
        work.setExperienceId(id);
        work.setUserId(USER_ID);
        work.setActorProfileId(PROFILE_ID);
        work.setDramaName("作品" + id);
        work.setRoleName("角色" + id);
        work.setSourceType(sourceType);
        return work;
    }
}
