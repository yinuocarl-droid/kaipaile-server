package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.dto.ActorAssetPageRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingItemRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardWork;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.service.actor.ActorCardMediaBindingService;
import com.kaipai.service.actor.ActorMediaAssetService;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActorCardPublicServiceImplTest {

    private ActorCardMapper cardMapper;
    private ActorCardWorkMapper workMapper;
    private ActorCardMediaBindingService bindingService;
    private ActorMediaAssetMapper assetMapper;
    private ActorProfileMapper profileMapper;
    private ActorMediaAssetService assetService;
    private AiGeneratedImageStorage generatedImageStorage;
    private ActorCardPublicServiceImpl service;

    @BeforeEach
    void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        workMapper = mock(ActorCardWorkMapper.class);
        assetMapper = mock(ActorMediaAssetMapper.class);
        bindingService = mock(ActorCardMediaBindingService.class);
        profileMapper = mock(ActorProfileMapper.class);
        assetService = mock(ActorMediaAssetService.class);
        generatedImageStorage = mock(AiGeneratedImageStorage.class);
        when(generatedImageStorage.issueReadUrl(anyString(), any(Duration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new ActorCardPublicServiceImpl(
                cardMapper,
                workMapper,
                assetMapper,
                profileMapper,
                bindingService,
                assetService,
                generatedImageStorage,
                new ObjectMapper());
    }

    @Test
    void publicViewUsesSignedBindingUrlsAndLegacyFallbackThroughOneContract() {
        ActorCard card = publishedCard(11L);
        card.setBackgroundImageUrl("https://cdn.test/full-card-background.jpg");
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(workMapper.selectList(any())).thenReturn(List.of());
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null))
                .thenReturn(binding("source_image", "https://signed.test/source"));
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.LIFE_PHOTO, null))
                .thenReturn(binding("life_photo", "https://legacy.test/photo"));
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.VIDEO_RESUME, null))
                .thenReturn(binding("video_resume", "https://signed.test/video"));

        var result = service.getPublicView(11L);

        assertEquals("https://signed.test/source", result.getBackgroundImageUrl());
        assertEquals("https://signed.test/source", result.getPreviewImageUrl());
        assertEquals(List.of("https://legacy.test/photo"), result.getPhotos());
        assertEquals("https://signed.test/video", result.getVideo().getUrl());
    }

    @Test
    void publicViewReturnsOneMainVisualInBothCompatibilityFields() {
        ActorCard card = publishedCard(11L);
        card.setExpandedImageUrl("https://cdn.test/expanded.jpg");
        card.setSourceImageUrl("https://cdn.test/source.jpg");
        card.setBackgroundImageUrl("https://cdn.test/background.jpg");
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(workMapper.selectList(any())).thenReturn(List.of());
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null))
                .thenReturn(binding("source_image", "https://signed.test/source"));

        var result = service.getPublicView(11L);

        assertEquals("https://cdn.test/expanded.jpg", result.getPreviewImageUrl());
        assertEquals(result.getPreviewImageUrl(), result.getBackgroundImageUrl());
    }

    @Test
    void privateBucketExpandedResultIsSignedOnlyForTheResponse() {
        ActorCard card = publishedCard(11L);
        card.setExpandedImageUrl("https://private.test/actor-card-expand/result.png");
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(workMapper.selectList(any())).thenReturn(List.of());
        when(generatedImageStorage.issueReadUrl(
                "https://private.test/actor-card-expand/result.png", Duration.ofMinutes(10)))
                .thenReturn("https://private.test/actor-card-expand/result.png?signature=test");

        var result = service.getPublicView(11L);

        assertEquals("https://private.test/actor-card-expand/result.png?signature=test",
                result.getPreviewImageUrl());
        assertEquals(result.getPreviewImageUrl(), result.getBackgroundImageUrl());
        assertEquals("https://private.test/actor-card-expand/result.png", card.getExpandedImageUrl());
    }

    @Test
    void signingFailureFallsBackToTheCurrentSourceBinding() {
        ActorCard card = publishedCard(11L);
        card.setExpandedImageUrl("https://private.test/actor-card-expand/result.png");
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(workMapper.selectList(any())).thenReturn(List.of());
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null))
                .thenReturn(binding("source_image", "https://signed.test/source"));
        when(generatedImageStorage.issueReadUrl(
                "https://private.test/actor-card-expand/result.png", Duration.ofMinutes(10)))
                .thenThrow(new IllegalStateException("signing unavailable"));

        var result = service.getPublicView(11L);

        assertEquals("https://signed.test/source", result.getPreviewImageUrl());
        assertEquals(result.getPreviewImageUrl(), result.getBackgroundImageUrl());
    }

    @Test
    void ownerPreviewAllowsOwnedDraftAndUsesTheSameAssembler() {
        ActorCard card = publishedCard(11L);
        card.setStatus("draft");
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(workMapper.selectList(any())).thenReturn(List.of());
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null))
                .thenReturn(binding("source_image", "https://signed.test/source"));
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.LIFE_PHOTO, null))
                .thenReturn(binding("life_photo", "https://signed.test/photo"));
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.VIDEO_RESUME, null))
                .thenReturn(binding("video_resume", "https://signed.test/video"));

        var result = service.getOwnerPreview(7L, 11L);

        assertEquals(11L, result.getId());
        assertEquals("https://signed.test/source", result.getPreviewImageUrl());
        assertEquals(List.of("https://signed.test/photo"), result.getPhotos());
    }

    @Test
    void ownerPreviewDoesNotLeakAnotherUsersCard() {
        ActorCard card = publishedCard(11L);
        card.setStatus("draft");
        when(cardMapper.selectById(11L)).thenReturn(card);

        BizException error = assertThrows(BizException.class,
                () -> service.getOwnerPreview(99L, 11L));

        assertEquals("演员卡不存在", error.getMessage());
    }

    @Test
    void profileSnapshotWinsAndCareerProfileFillsMissingShareFields() {
        ActorCard card = publishedCard(11L);
        card.setProfileSnapshotJson("""
                {"name":"快照姓名","height":"168","contact":"13800000000"}
                """);
        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(3L);
        profile.setUserId(7L);
        profile.setNickName("档案姓名");
        profile.setGender(2);
        profile.setAge(24);
        profile.setWeight(48);
        profile.setLocationCity("上海");
        profile.setLanguageTagsJson("[\"普通话\",\"英语\"]");
        profile.setSpecialtyTagsJson("[\"影视表演\",\"短剧\"]");
        profile.setProfessionalAbilityTagsJson("[\"台词\",\"眼神戏\"]");
        profile.setRoleTypeTagsJson("[\"正派\",\"反派\"]");
        profile.setExtendedField("{\"bodyType\":\"标准\",\"hairStyle\":\"长发\"}");
        profile.setIsCertified(true);

        when(cardMapper.selectById(11L)).thenReturn(card);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(workMapper.selectList(any())).thenReturn(List.of());

        var result = service.getPublicView(11L);

        assertEquals("快照姓名", result.getProfile().getName());
        assertEquals("168", result.getProfile().getHeight());
        assertEquals("female", result.getProfile().getGender());
        assertEquals(24, result.getProfile().getAge());
        assertEquals("标准", result.getProfile().getBodyType());
        assertEquals(List.of("普通话", "英语"), result.getProfile().getLanguages());
        assertEquals(List.of("影视表演", "短剧", "台词", "眼神戏", "正派", "反派"), result.getProfile().getSkills());
        assertEquals(List.of("影视表演", "短剧"), result.getProfile().getSkillGroups().getSpecialty());
        assertEquals(List.of("台词", "眼神戏"), result.getProfile().getSkillGroups().getProfessionalAbility());
        assertEquals(List.of("正派", "反派"), result.getProfile().getSkillGroups().getRoleType());
        assertEquals(true, result.getProfile().getCertified());
    }

    @Test
    void structuredSnapshotSkillGroupsTakePriorityAndWorkTypeStaysOutOfSkills() {
        ActorCard card = publishedCard(11L);
        card.setProfileSnapshotJson("""
                {"specialtyTags":["主持"],"professionalAbilityTags":["同期声"],"roleTypeTags":["反派"],"skills":["旧技能"]}
                """);
        ActorProfile profile = new ActorProfile();
        profile.setUserId(7L);
        profile.setSpecialtyTagsJson("[\"表演\"]");
        profile.setProfessionalAbilityTagsJson("[\"台词\"]");
        profile.setRoleTypeTagsJson("[\"正派\"]");

        ActorCardWork work = new ActorCardWork();
        work.setId(101L);
        work.setCardId(11L);
        work.setWorkTitle("作品");
        work.setWorkType("短剧");
        when(cardMapper.selectById(11L)).thenReturn(card);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(workMapper.selectList(any())).thenReturn(List.of(work));

        var result = service.getPublicView(11L);

        assertEquals(List.of("主持"), result.getProfile().getSkillGroups().getSpecialty());
        assertEquals(List.of("同期声"), result.getProfile().getSkillGroups().getProfessionalAbility());
        assertEquals(List.of("反派"), result.getProfile().getSkillGroups().getRoleType());
        assertEquals(List.of("旧技能", "表演", "台词", "正派"), result.getProfile().getSkills());
        assertEquals("短剧", result.getWorks().get(0).getWorkType());
    }

    @Test
    void attachmentIncludesSignedPdfPagesWithoutOwnerSideFrontendRequest() {
        ActorCard card = publishedCard(11L);
        card.setAttachmentAssetId(99L);
        ActorMediaAsset asset = new ActorMediaAsset();
        asset.setAssetId(99L);
        asset.setOriginalName("演员简历.pdf");

        when(cardMapper.selectById(11L)).thenReturn(card);
        when(workMapper.selectList(any())).thenReturn(List.of());
        when(assetMapper.selectById(99L)).thenReturn(asset);
        when(assetService.listPages(7L, 99L)).thenReturn(List.of(
                new ActorAssetPageRespDTO(1, "https://signed.test/page-1", Instant.now())));

        var result = service.getPublicView(11L);

        assertEquals("演员简历.pdf", result.getAttachment().getFilename());
        assertEquals(List.of("https://signed.test/page-1"), result.getAttachment().getPageImages());
    }

    @Test
    void invalidTemporaryCardMediaFallsBackToPersistentCareerProfileMedia() {
        ActorCard card = publishedCard(11L);
        ActorProfile profile = new ActorProfile();
        profile.setActorProfileId(3L);
        profile.setUserId(7L);
        profile.setAvatarUrl("https://cdn.test/avatar.jpg");
        profile.setPhotoUrls("[\"https://cdn.test/photo-1.jpg\",\"https://cdn.test/photo-2.jpg\"]");
        profile.setVideoUrl("https://cdn.test/resume.mp4");

        when(cardMapper.selectById(11L)).thenReturn(card);
        when(profileMapper.selectOne(any())).thenReturn(profile);
        when(workMapper.selectList(any())).thenReturn(List.of());
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.SOURCE_IMAGE, null))
                .thenReturn(binding("source_image", "http://tmp/source.jpg"));
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.LIFE_PHOTO, null))
                .thenReturn(binding("life_photo", "http://tmp/photo.jpg"));
        when(bindingService.list(7L, 11L, ActorCardMediaBindingServiceImpl.VIDEO_RESUME, null))
                .thenReturn(binding("video_resume", "http://tmp/video.mp4"));

        var result = service.getPublicView(11L);

        assertEquals("https://cdn.test/avatar.jpg", result.getPreviewImageUrl());
        assertEquals(List.of("https://cdn.test/photo-1.jpg", "https://cdn.test/photo-2.jpg"), result.getPhotos());
        assertEquals("https://cdn.test/resume.mp4", result.getVideo().getUrl());
    }

    @Test
    void copyRemapsWorkIdsBeforeCopyingMediaBindings() {
        ActorCard source = publishedCard(11L);
        when(cardMapper.selectById(11L)).thenReturn(source);
        when(cardMapper.insert(any(ActorCard.class))).thenAnswer(invocation -> {
            ActorCard card = invocation.getArgument(0);
            card.setId(12L);
            return 1;
        });
        ActorCardWork sourceWork = new ActorCardWork();
        sourceWork.setId(101L);
        sourceWork.setCardId(11L);
        sourceWork.setWorkTitle("作品");
        when(workMapper.selectList(any())).thenReturn(List.of(sourceWork));
        when(workMapper.insert(any(ActorCardWork.class))).thenAnswer(invocation -> {
            ActorCardWork work = invocation.getArgument(0);
            work.setId(202L);
            return 1;
        });

        service.copy(7L, 11L);

        verify(bindingService).copyBindings(7L, 11L, 12L, Map.of(101L, 202L));
    }

    private ActorCard publishedCard(Long cardId) {
        ActorCard card = new ActorCard();
        card.setId(cardId);
        card.setUserId(7L);
        card.setStatus("published");
        card.setTitle("演员卡");
        return card;
    }

    private ActorCardMediaBindingRespDTO binding(String usage, String url) {
        ActorCardMediaBindingItemRespDTO item = new ActorCardMediaBindingItemRespDTO();
        item.setPreviewUrl(url);
        item.setSortOrder(0);
        ActorCardMediaBindingRespDTO response = new ActorCardMediaBindingRespDTO();
        response.setUsage(usage);
        response.setItems(List.of(item));
        return response;
    }
}
