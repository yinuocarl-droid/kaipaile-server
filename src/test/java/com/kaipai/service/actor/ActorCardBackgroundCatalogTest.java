package com.kaipai.service.actor;
import com.kaipai.mapper.actor.ActorCardBackgroundMapper;
import com.kaipai.model.actor.card.entity.ActorCardBackground;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class ActorCardBackgroundCatalogTest {
 @Mock ActorCardBackgroundMapper mapper;
 @Mock AiGeneratedImageStorage storage;
 @Mock ActorCardDecorationAssignmentService assignmentService;
 @InjectMocks ActorCardBackgroundService service;
 @Test void miniProgramReceivesResponseSignedUrlsNotStablePrivateObjects() {
  var bg=new ActorCardBackground();bg.setId(3L);bg.setStyle("classic");bg.setImageUrl("https://bucket.cos/a.png");bg.setThumbnailUrl(bg.getImageUrl());bg.setEnabled(1);bg.setSortOrder(2);
  when(mapper.selectList(any())).thenReturn(List.of(bg));
  lenient().when(storage.issueReadUrl(eq(bg.getImageUrl()),any(Duration.class))).thenReturn("https://bucket.cos/a.png?signed=1");
  var response=service.listByStyle("classic");
  assertEquals("https://bucket.cos/a.png?signed=1",response.getImages().get(0).getImageUrl());
  assertEquals("https://bucket.cos/a.png",bg.getImageUrl(),"never persist short signatures");
 }
 @Test void readinessEnrichmentDoesNotFilterOrReorderTheEnabledCatalog() {
  var first=background(22L,9);var second=background(13L,12);
  when(mapper.selectList(any())).thenReturn(List.of(first,second));
  when(storage.issueReadUrl(anyString(),any(Duration.class))).thenAnswer(i->i.getArgument(0));
  var readiness=new LinkedHashMap<Long,ActorCardDecorationAssignmentService.DecorationReadiness>();
  readiness.put(22L,new ActorCardDecorationAssignmentService.DecorationReadiness(true,null));
  readiness.put(13L,new ActorCardDecorationAssignmentService.DecorationReadiness(false,"当前背景尚未绑定已发布装修版本"));
  when(assignmentService.backgroundReadiness(List.of(22L,13L))).thenReturn(readiness);

  var images=service.listByStyle("classic").getImages();

  assertEquals(List.of(22L,13L),images.stream().map(x->x.getId()).toList());
  assertTrue(images.get(0).isDecorationReady());assertNull(images.get(0).getDecorationUnavailableReason());
  assertFalse(images.get(1).isDecorationReady());
  assertEquals("当前背景尚未绑定已发布装修版本",images.get(1).getDecorationUnavailableReason());
 }
 // 00-267: 旧客户端传 ancient、或迁移前库里仍是 ancient，都按 costume 风格返回
 @Test void legacyAncientRequestReturnsTheCostumeStyle() {
  when(mapper.selectList(any())).thenReturn(List.of());
  assertEquals("costume",service.listByStyle("ancient").getStyle());
  assertEquals("costume",service.listByStyle("costume").getStyle());
 }
 @Test void blankStyleReturnsEmptyWithoutQuerying() {
  assertTrue(service.listByStyle(" ").getImages().isEmpty());
  verifyNoInteractions(mapper);
 }
 private ActorCardBackground background(long id,int sortOrder){
  var bg=new ActorCardBackground();bg.setId(id);bg.setStyle("classic");bg.setImageUrl("https://bucket.cos/"+id+".png");bg.setThumbnailUrl(bg.getImageUrl());bg.setEnabled(1);bg.setSortOrder(sortOrder);return bg;
 }
}
