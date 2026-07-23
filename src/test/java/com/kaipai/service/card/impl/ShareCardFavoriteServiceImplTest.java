package com.kaipai.service.card.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.card.ShareCardFavoriteMapper;
import com.kaipai.mapper.card.UserShareCardMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.model.card.entity.ShareCardFavorite;
import com.kaipai.model.card.entity.UserShareCard;
import com.kaipai.service.card.CardSceneTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ShareCardFavoriteServiceImplTest {
    private ShareCardFavoriteMapper favoriteMapper; private UserShareCardMapper cardMapper; private ActorProfileMapper profileMapper; private CardSceneTemplateService templateService; private ShareCardFavoriteServiceImpl service;
    @BeforeEach void setUp(){favoriteMapper=mock(ShareCardFavoriteMapper.class);cardMapper=mock(UserShareCardMapper.class);profileMapper=mock(ActorProfileMapper.class);templateService=mock(CardSceneTemplateService.class);service=new ShareCardFavoriteServiceImpl(favoriteMapper,cardMapper,profileMapper,templateService);}
    @Test void addAndRemoveAreIdempotentAndOwnCardIsRejected(){
        UserShareCard card=card(20L,"active"); when(cardMapper.selectOne(any())).thenReturn(card);
        when(favoriteMapper.selectOne(any())).thenReturn(null,new ShareCardFavorite(),new ShareCardFavorite(),null);
        assertTrue(service.add(10L,5L).isFavorited()); assertTrue(service.add(10L,5L).isFavorited());
        assertFalse(service.remove(10L,5L).isFavorited()); assertFalse(service.remove(10L,5L).isFavorited());
        card.setUserId(10L); assertThrows(BizException.class,()->service.add(10L,5L));
    }
    @Test void inactiveCardCannotBeFavorited(){when(cardMapper.selectOne(any())).thenReturn(null);assertThrows(BizException.class,()->service.add(10L,5L));}
    @Test void concurrentUniqueCollisionStillReturnsFavorited(){when(cardMapper.selectOne(any())).thenReturn(card(20L,"active"));when(favoriteMapper.selectOne(any())).thenReturn(null);when(favoriteMapper.insert(any())).thenThrow(new DuplicateKeyException("race"));assertTrue(service.add(10L,5L).isFavorited());}
    @Test void listReturnsOnlyFavoritesWhoseCardsRemainActive(){
        ShareCardFavorite favorite=new ShareCardFavorite();favorite.setFavoriteId(1L);favorite.setUserId(10L);favorite.setShareCardId(5L);
        when(favoriteMapper.selectList(any())).thenReturn(java.util.List.of(favorite));
        UserShareCard card=card(20L,"active");card.setTemplateId(3L);when(cardMapper.selectList(any())).thenReturn(java.util.List.of(card));
        ActorProfile profile=new ActorProfile();profile.setUserId(20L);profile.setNickName("王火火");profile.setAvatarUrl("avatar.jpg");profile.setIntro("演员简介");when(profileMapper.selectList(any())).thenReturn(java.util.List.of(profile));
        ActorSceneTemplateRespDTO template=new ActorSceneTemplateRespDTO();template.setTemplateId(3L);template.setTemplateSceneCode("classic");template.setName("经典名片");when(templateService.actorSceneTemplates()).thenReturn(java.util.List.of(template));
        var result=service.list(10L,1,10);
        assertEquals(1,result.getTotal());var item=result.getList().get(0);assertEquals(5L,item.getShareCardId());assertEquals("王火火",item.getActorName());assertEquals("avatar.jpg",item.getActorAvatar());assertEquals("classic",item.getTemplateSceneCode());assertEquals("经典名片",item.getTemplateName());assertEquals("演员简介",item.getIntro());
    }
    @Test void stateReadsActiveFavoriteWithoutMutation(){
        when(cardMapper.selectOne(any())).thenReturn(card(20L,"active"));
        ShareCardFavorite favorite=new ShareCardFavorite();favorite.setShareCardId(5L);favorite.setUserId(10L);
        when(favoriteMapper.selectOne(any())).thenReturn(favorite,null);
        assertTrue(service.state(10L,5L).isFavorited());
        assertFalse(service.state(10L,5L).isFavorited());
        verify(favoriteMapper,never()).insert(any());
        verify(favoriteMapper,never()).deleteById(any());
    }
    private UserShareCard card(Long owner,String status){UserShareCard c=new UserShareCard();c.setShareCardId(5L);c.setUserId(owner);c.setShareStatus(status);return c;}
}
