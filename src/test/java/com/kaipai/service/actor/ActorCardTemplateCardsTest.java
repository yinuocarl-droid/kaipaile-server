package com.kaipai.service.actor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kaipai.mapper.actor.ActorCardBackgroundAdminMapper;
import com.kaipai.mapper.actor.ActorCardBackgroundMapper;
import com.kaipai.model.actor.card.entity.ActorCardBackground;
import com.kaipai.model.actor.card.entity.ActorCardDecorationEditorContext;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.service.card.CardSceneTemplateService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;

/** 00-267 3.4: 首页「模板创建」每个启用风格模板一张卡，封面 = 模板「页面装修」的编辑背景。 */
@ExtendWith(MockitoExtension.class)
class ActorCardTemplateCardsTest {
    @Mock ActorCardBackgroundMapper mapper;
    @Mock AiGeneratedImageStorage storage;
    @Mock ActorCardDecorationAssignmentService assignments;
    @Mock CardSceneTemplateService templates;
    @Mock ActorCardBackgroundAdminMapper adminMapper;
    @InjectMocks ActorCardBackgroundService service;

    @Test void oneCardPerEnabledTemplateInAdminOrderWithEditorBackgroundCover() {
        when(templates.actorSceneTemplates()).thenReturn(List.of(template(2L, "urban", "都市"), template(3L, "costume", "古风"), template(1L, "classic", "经典")));
        when(adminMapper.context(2L)).thenReturn(context(22L));
        when(adminMapper.context(3L)).thenReturn(null);
        when(adminMapper.context(1L)).thenReturn(context(null));
        when(mapper.selectById(22L)).thenReturn(background(22L, "classic", 1));
        when(storage.issueReadUrl(anyString(), any(Duration.class))).thenAnswer(i -> i.getArgument(0) + "?sign=1");
        when(assignments.backgroundReadiness(List.of(22L))).thenReturn(Map.of(22L, new ActorCardDecorationAssignmentService.DecorationReadiness(true, null)));

        var cards = service.templateCards();

        assertEquals(List.of("urban", "costume", "classic"), cards.stream().map(c -> c.getTemplateSceneCode()).toList());
        assertEquals("都市", cards.get(0).getName());
        assertEquals(22L, cards.get(0).getBackground().getId(), "背景分类是 classic 也照样作为都市模板的封面");
        assertEquals("https://bucket.cos/22.png?sign=1", cards.get(0).getBackground().getImageUrl());
        assertTrue(cards.get(0).getBackground().isDecorationReady());
        assertNull(cards.get(1).getBackground());
        assertNull(cards.get(2).getBackground());
    }

    @Test void disabledEditorBackgroundCountsAsNotConfigured() {
        when(templates.actorSceneTemplates()).thenReturn(List.of(template(2L, "urban", "都市")));
        when(adminMapper.context(2L)).thenReturn(context(7L));
        when(mapper.selectById(7L)).thenReturn(background(7L, "costume", 0));

        assertNull(service.templateCards().get(0).getBackground());
        verifyNoInteractions(storage, assignments);
    }

    @Test void missingEditorContextTableStillListsTemplates() {
        when(templates.actorSceneTemplates()).thenReturn(List.of(template(2L, "urban", "都市")));
        when(adminMapper.context(2L)).thenThrow(new BadSqlGrammarException("ctx", "SELECT", new java.sql.SQLException("no table")));

        var cards = service.templateCards();

        assertEquals(1, cards.size());
        assertNull(cards.get(0).getBackground());
    }

    private static ActorSceneTemplateRespDTO template(Long id, String code, String name) {
        var t = new ActorSceneTemplateRespDTO(); t.setTemplateId(id); t.setTemplateSceneCode(code); t.setName(name); return t;
    }

    private static ActorCardDecorationEditorContext context(Long backgroundId) {
        var c = new ActorCardDecorationEditorContext(); c.setBackgroundId(backgroundId); return c;
    }

    private static ActorCardBackground background(Long id, String style, int enabled) {
        var b = new ActorCardBackground(); b.setId(id); b.setStyle(style); b.setEnabled(enabled);
        b.setImageUrl("https://bucket.cos/" + id + ".png"); b.setThumbnailUrl(b.getImageUrl()); b.setSortOrder(0); return b;
    }
}
