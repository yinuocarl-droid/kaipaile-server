package com.kaipai.service.card.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.card.ShareCardFavoriteMapper;
import com.kaipai.mapper.card.UserShareCardMapper;
import com.kaipai.model.card.entity.ShareCardFavorite;
import com.kaipai.model.card.entity.UserShareCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ShareCardFavoriteServiceImplTest {
    private ShareCardFavoriteMapper favoriteMapper; private UserShareCardMapper cardMapper; private ShareCardFavoriteServiceImpl service;
    @BeforeEach void setUp(){favoriteMapper=mock(ShareCardFavoriteMapper.class);cardMapper=mock(UserShareCardMapper.class);service=new ShareCardFavoriteServiceImpl(favoriteMapper,cardMapper);}
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
        when(cardMapper.selectList(any())).thenReturn(java.util.List.of(card(20L,"active")));
        var result=service.list(10L,1,10);
        assertEquals(1,result.getTotal());assertEquals(5L,result.getList().get(0).getShareCardId());
    }
    private UserShareCard card(Long owner,String status){UserShareCard c=new UserShareCard();c.setShareCardId(5L);c.setUserId(owner);c.setShareStatus(status);return c;}
}
