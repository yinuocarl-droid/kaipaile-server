package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.actor.ActorProfileRepresentativeWorkMapper;
import com.kaipai.model.actor.dto.ActorRepresentativeWorksUpdateDTO;
import com.kaipai.model.actor.dto.ActorWorkQueryDTO;
import com.kaipai.model.actor.dto.ActorWorkSaveDTO;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.actor.entity.ActorProfileRepresentativeWork;
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
        when(experienceMapper.insert(any())).thenReturn(1);
        when(experienceMapper.updateById(any())).thenReturn(1);
        when(experienceMapper.deleteById(any())).thenReturn(1);
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

        BizException error = assertThrows(BizException.class,
                () -> service.replaceRepresentativeWorks(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), error.getCode());
        verify(representativeMapper, never()).delete(any());
        verify(representativeMapper, never()).insert(any());
        verify(profileMapper, never()).incrementWorkLibraryVersion(11L);
    }

    @Test
    void representativeWorksRejectDuplicateIds() {
        ActorRepresentativeWorksUpdateDTO request = new ActorRepresentativeWorksUpdateDTO();
        request.setExperienceIds(List.of(1L, 2L, 2L));

        BizException error = assertThrows(BizException.class,
                () -> service.replaceRepresentativeWorks(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), error.getCode());
        verify(representativeMapper, never()).delete(any());
        verify(representativeMapper, never()).insert(any());
        verify(profileMapper, never()).incrementWorkLibraryVersion(11L);
    }

    @Test
    void representativeWorksReturnsCurrentSelectionInConfiguredOrder() {
        ActorProfileRepresentativeWork second = representative(22L, 2);
        ActorProfileRepresentativeWork first = representative(21L, 1);
        when(representativeMapper.selectList(any())).thenReturn(List.of(first, second));
        when(experienceMapper.selectList(any())).thenReturn(List.of(workEntity(22L), workEntity(21L)));

        var result = service.representativeWorks(7L);

        assertEquals(List.of(21L, 22L), result.stream().map(item -> item.getExperienceId()).toList());
    }

    @Test
    void successfulCreateIncrementsWorkLibraryVersionExactlyOnce() {
        service.createWork(7L, workSave("测试作品", "角色"));

        verify(profileMapper, times(1)).incrementWorkLibraryVersion(11L);
    }

    @Test
    void successfulUpdateIncrementsWorkLibraryVersionExactlyOnce() {
        when(experienceMapper.selectOne(any())).thenReturn(workEntity(21L));

        service.updateWork(7L, 21L, workSave("更新作品", "更新角色"));

        verify(profileMapper, times(1)).incrementWorkLibraryVersion(11L);
    }

    @Test
    void successfulDeleteIncrementsWorkLibraryVersionExactlyOnce() {
        when(experienceMapper.selectOne(any())).thenReturn(workEntity(21L));

        service.deleteWork(7L, 21L);

        verify(profileMapper, times(1)).incrementWorkLibraryVersion(11L);
    }

    @Test
    void successfulRepresentativeReplacementIncrementsWorkLibraryVersionExactlyOnce() {
        ActorRepresentativeWorksUpdateDTO request = new ActorRepresentativeWorksUpdateDTO();
        request.setExperienceIds(List.of(21L, 22L));
        when(experienceMapper.selectList(any())).thenReturn(List.of(workEntity(21L), workEntity(22L)));

        service.replaceRepresentativeWorks(7L, request);

        verify(profileMapper, times(1)).incrementWorkLibraryVersion(11L);
    }

    @Test
    void internalImportCreateWritesImportSourceWithoutIncrementingVersion() {
        var created = service.createImportedWork(7L, workSave("导入作品", "女主"));

        assertEquals("import", created.getSourceType());
        verify(profileMapper, never()).incrementWorkLibraryVersion(11L);
    }

    @Test
    void internalImportMergePreservesStoredSourceWithoutIncrementingVersion() {
        ActorExperience existing = workEntity(21L);
        existing.setSourceType("migration");
        when(experienceMapper.selectOne(any())).thenReturn(existing);

        var updated = service.updateImportedWork(
                7L, 21L, workSave("导入更新作品", "女二"));

        assertEquals("migration", updated.getSourceType());
        verify(profileMapper, never()).incrementWorkLibraryVersion(11L);
    }

    @Test
    void failedWorkInsertFailsClosedBeforeVersionIncrement() {
        when(experienceMapper.insert(any())).thenReturn(0);

        BizException error = assertThrows(
                BizException.class,
                () -> service.createImportedWork(7L, workSave("导入作品", "女主")));

        assertEquals(46008, error.getCode());
        verify(profileMapper, never()).incrementWorkLibraryVersion(11L);
    }

    @Test
    void failedWorkUpdateFailsClosedBeforeVersionIncrement() {
        when(experienceMapper.selectOne(any())).thenReturn(workEntity(21L));
        when(experienceMapper.updateById(any())).thenReturn(0);

        BizException error = assertThrows(
                BizException.class,
                () -> service.updateImportedWork(7L, 21L, workSave("导入更新作品", "女二")));

        assertEquals(46008, error.getCode());
        verify(profileMapper, never()).incrementWorkLibraryVersion(11L);
    }

    private ActorProfileRepresentativeWork representative(Long experienceId, int sortNo) {
        ActorProfileRepresentativeWork relation = new ActorProfileRepresentativeWork();
        relation.setActorProfileId(11L);
        relation.setExperienceId(experienceId);
        relation.setSortNo(sortNo);
        return relation;
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
        work.setUserId(7L);
        work.setActorProfileId(11L);
        work.setDramaName("作品" + id);
        work.setRoleName("角色" + id);
        work.setSourceType("manual");
        work.setDeleted(0);
        return work;
    }
}
