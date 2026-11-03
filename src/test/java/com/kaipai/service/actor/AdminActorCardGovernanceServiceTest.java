package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kaipai.controller.admin.content.AdminActorCardGovernanceController;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.actor.card.dto.ActorCardListItemDTO;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernanceItemDTO;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernancePageDTO;
import com.kaipai.model.actor.card.dto.AdminActorCardGovernanceQueryDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.user.entity.User;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 00-265: 后台演员卡治理列表必须与小程序名片夹同源（actor_card），
 * 页签计数为全局计数，不随分页和当前状态页签变化。
 */
class AdminActorCardGovernanceServiceTest {

    private ActorCardMapper cardMapper;
    private UserMapper userMapper;
    private ActorProfileMapper profileMapper;
    private ActorCardPublishService publishService;
    private AdminActorCardGovernanceService service;
    private final List<LambdaQueryWrapper<ActorCard>> countWrappers = new ArrayList<>();

    @BeforeAll static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ActorCard.class);
        TableInfoHelper.initTableInfo(assistant, ActorProfile.class);
    }

    @SuppressWarnings("unchecked")
    @BeforeEach void setUp() {
        cardMapper = mock(ActorCardMapper.class);
        userMapper = mock(UserMapper.class);
        profileMapper = mock(ActorProfileMapper.class);
        publishService = mock(ActorCardPublishService.class);
        service = new AdminActorCardGovernanceService(cardMapper, userMapper, profileMapper, publishService);
        countWrappers.clear();
        when(cardMapper.selectCount(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<ActorCard> wrapper = invocation.getArgument(0);
            countWrappers.add(wrapper);
            String sql = wrapper.getSqlSegment();
            if (!sql.contains("status")) return 4L;
            return wrapper.getParamNameValuePairs().containsValue("published") ? 3L : 1L;
        });
        when(cardMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ActorCard> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());
        when(profileMapper.selectList(any())).thenReturn(List.of());
    }

    @Test void statusCountsAreGlobalAndIgnoreTheActiveStatusTab() {
        AdminActorCardGovernanceQueryDTO query = new AdminActorCardGovernanceQueryDTO();
        query.setStatus("draft");

        AdminActorCardGovernancePageDTO result = service.list(query);

        assertEquals(4, result.getStatusCounts().getTotal());
        assertEquals(3, result.getStatusCounts().getPublished());
        assertEquals(1, result.getStatusCounts().getDraft());
        assertFalse(countWrappers.get(0).getParamNameValuePairs().containsValue("draft"),
                "total 计数不能被当前状态页签过滤");
    }

    @Test void filtersApplyToBothListAndCountsButStatusOnlyToTheList() {
        AdminActorCardGovernanceQueryDTO query = new AdminActorCardGovernanceQueryDTO();
        query.setUserId(7L);
        query.setCardId(22L);
        query.setStyle(" urban ");
        query.setStatus("published");

        service.list(query);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ActorCard>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(cardMapper).selectPage(any(), captor.capture());
        String listSql = captor.getValue().getSqlSegment();
        var listParams = captor.getValue().getParamNameValuePairs().values();
        assertTrue(listParams.containsAll(List.of(7L, 22L, "urban", "published")));
        assertTrue(listSql.contains("ORDER BY"));
        var totalParams = countWrappers.get(0).getParamNameValuePairs().values();
        assertTrue(totalParams.containsAll(List.of(7L, 22L, "urban")));
        assertFalse(totalParams.contains("published"));
    }

    @Test void unknownStatusReturnsAnEmptyPageWithoutQueryingTheList() {
        AdminActorCardGovernanceQueryDTO query = new AdminActorCardGovernanceQueryDTO();
        query.setStatus("archived");

        AdminActorCardGovernancePageDTO result = service.list(query);

        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
        assertEquals(4, result.getStatusCounts().getTotal());
        verify(cardMapper, never()).selectPage(any(), any());
    }

    @Test void itemsCarryOwnerAndTheSameCompletionAndCoverAsTheCardList() {
        ActorCard card = new ActorCard();
        card.setId(22L);
        card.setUserId(7L);
        card.setStatus("published");
        card.setTitle("都市演员卡");
        card.setStyle("urban");
        card.setCurrentStep(7);
        card.setPublishedVersion(2);
        stubPage(card);
        User user = new User();
        user.setUserId(7L);
        user.setUserName("account-name");
        user.setPhone("13800000000");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user));
        ActorProfile profile = new ActorProfile();
        profile.setUserId(7L);
        profile.setNickName("林演员");
        when(profileMapper.selectList(any())).thenReturn(List.of(profile));
        ActorCardListItemDTO described = new ActorCardListItemDTO();
        described.setCompletionPercentage(85);
        described.setCoverImageUrl("https://cdn.test/cover.jpg?sign=1");
        when(publishService.describe(card)).thenReturn(described);

        AdminActorCardGovernanceItemDTO item = service.list(new AdminActorCardGovernanceQueryDTO()).getList().get(0);

        assertEquals(22L, item.getCardId());
        assertEquals("林演员", item.getOwnerName());
        assertEquals("13800000000", item.getOwnerPhone(), "档案无手机号时回退账号手机号");
        assertEquals(85, item.getCompletionPercentage());
        assertEquals("https://cdn.test/cover.jpg?sign=1", item.getCoverImageUrl());
        assertEquals(7, item.getCurrentStep());
        assertEquals(2, item.getPublishedVersion());
    }

    @Test void aFailingDerivationDoesNotBreakTheList() {
        ActorCard card = new ActorCard();
        card.setId(9L);
        card.setUserId(3L);
        card.setStatus("draft");
        stubPage(card);
        when(publishService.describe(card)).thenThrow(new IllegalStateException("cos down"));

        AdminActorCardGovernanceItemDTO item = service.list(new AdminActorCardGovernanceQueryDTO()).getList().get(0);

        assertEquals(9L, item.getCardId());
        assertNull(item.getCoverImageUrl());
        assertNull(item.getOwnerName());
    }

    @Test void endpointReusesTheShareContentPagePermission() throws Exception {
        PreAuthorize auth = AdminActorCardGovernanceController.class
                .getMethod("list", AdminActorCardGovernanceQueryDTO.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("hasAuthority('page.content.share-cards')", auth.value());
    }

    private void stubPage(ActorCard card) {
        doAnswer(invocation -> {
            Page<ActorCard> page = invocation.getArgument(0);
            page.setRecords(List.of(card));
            page.setTotal(1);
            return page;
        }).when(cardMapper).selectPage(any(), any());
    }
}
