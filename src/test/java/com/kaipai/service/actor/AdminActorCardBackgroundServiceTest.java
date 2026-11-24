package com.kaipai.service.actor;
import com.kaipai.common.auth.*;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.*;
import com.kaipai.model.actor.card.dto.AdminActorCardBackgroundDTO.*;
import com.kaipai.model.actor.card.entity.*;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class AdminActorCardBackgroundServiceTest {
 @Mock ActorCardBackgroundMapper mapper;
 @Mock ActorCardBackgroundAdminMapper adminMapper;
 @Mock AiGeneratedImageStorage storage;
 @Mock AdminAuthContext auth;
 @Mock AdminOperationLogger audit;
 @InjectMocks AdminActorCardBackgroundService service;
 byte[] png() throws Exception { var b=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,6,BufferedImage.TYPE_INT_RGB),"png",b);return b.toByteArray(); }
 ActorCardBackground bg() {var b=new ActorCardBackground();b.setId(9L);b.setStyle("classic");b.setSortOrder(0);b.setEnabled(1);b.setImageUrl("https://bucket/a.png");return b;}
 @Test void uploadsDecodeNormalizeAndStoreOnlyStableUrlWithRequiredAudit() throws Exception {
  when(auth.getCurrentAdminUserId()).thenReturn(7L);
  when(storage.upload(any(),eq("image/png"),eq("actor-card-background"))).thenReturn("https://bucket/a.png");
  when(storage.issueReadUrl(anyString(),any())).thenReturn("https://bucket/a.png?signed");
  when(mapper.insert(any(ActorCardBackground.class))).thenAnswer(i->{i.getArgument(0,ActorCardBackground.class).setId(9L);return 1;});
  when(adminMapper.templateSceneCodeExists("classic")).thenReturn(1);
  var r=service.upload(new MockMultipartFile("file","foo.png","image/png",png()),"classic",0);
  assertEquals(9L,r.id());assertTrue(r.imageUrl().contains("signed"));
  var c=ArgumentCaptor.forClass(ActorCardBackground.class);verify(mapper).insert(c.capture());assertEquals("https://bucket/a.png",c.getValue().getImageUrl());verify(audit).logRequired(any());
 }
 @Test void rejectsPretendImageBeforeExternalUpload() {when(auth.getCurrentAdminUserId()).thenReturn(7L);when(adminMapper.templateSceneCodeExists("classic")).thenReturn(1);assertThrows(BizException.class,()->service.upload(new MockMultipartFile("file","x.png","image/png","not image".getBytes()),"classic",0));verifyNoInteractions(storage,mapper);}
 // 00-267: 风格必须是已存在的风格模板场景码
 @Test void rejectsStyleWithoutAStyleTemplate() throws Exception {when(auth.getCurrentAdminUserId()).thenReturn(7L);when(adminMapper.templateSceneCodeExists("fresh")).thenReturn(0);assertThrows(BizException.class,()->service.upload(new MockMultipartFile("file","x.png","image/png",png()),"fresh",0));verifyNoInteractions(storage,mapper);}
 @Test void legacyAncientUploadIsStoredAsTheCostumeTemplate() throws Exception {
  when(auth.getCurrentAdminUserId()).thenReturn(7L);when(adminMapper.templateSceneCodeExists("costume")).thenReturn(1);
  when(storage.upload(any(),eq("image/png"),eq("actor-card-background"))).thenReturn("https://bucket/a.png");when(storage.issueReadUrl(anyString(),any())).thenReturn("https://bucket/a.png?signed");
  when(mapper.insert(any(ActorCardBackground.class))).thenAnswer(i->{i.getArgument(0,ActorCardBackground.class).setId(9L);return 1;});
  var r=service.upload(new MockMultipartFile("file","x.png","image/png",png()),"ancient",0);
  assertEquals("costume",r.style());
 }
 @Test void legacyCategoryCanStayWhileEditingOrderOrEnabled() {
  when(auth.getCurrentAdminUserId()).thenReturn(7L);var b=bg();b.setStyle("fresh");when(adminMapper.lockBackground(9L)).thenReturn(b);
  when(adminMapper.updateMetadata(9L,"fresh",5,0)).thenReturn(1);when(storage.issueReadUrl(anyString(),any())).thenReturn("https://bucket/a.png?signed");
  assertEquals(0,service.update(9L,new UpdateReq("fresh",5,0,"fresh",0,1)).enabled());
  verify(adminMapper,never()).templateSceneCodeExists(anyString());
 }
 @Test void movingIntoACategoryWithoutTemplateIsRejected() {
  when(auth.getCurrentAdminUserId()).thenReturn(7L);when(adminMapper.lockBackground(9L)).thenReturn(bg());when(adminMapper.templateSceneCodeExists("fresh")).thenReturn(0);
  assertThrows(BizException.class,()->service.update(9L,new UpdateReq("fresh",0,1,"classic",0,1)));
  verify(adminMapper,never()).updateMetadata(anyLong(),anyString(),anyInt(),anyInt());
 }
 @Test void listedStyleIsNormalizedSoCompareAndSwapAcceptsIt() {
  when(auth.getCurrentAdminUserId()).thenReturn(7L);var b=bg();b.setStyle("ancient");when(adminMapper.lockBackground(9L)).thenReturn(b);
  when(adminMapper.updateMetadata(9L,"costume",3,1)).thenReturn(1);when(storage.issueReadUrl(anyString(),any())).thenReturn("https://bucket/a.png?signed");
  assertEquals("costume",service.update(9L,new UpdateReq("costume",3,1,"costume",0,1)).style());
 }
 @Test void rejectsOversizeBeforeReadOrExternalCall() {when(auth.getCurrentAdminUserId()).thenReturn(7L);when(adminMapper.templateSceneCodeExists("urban")).thenReturn(1);var f=mock(org.springframework.web.multipart.MultipartFile.class);when(f.isEmpty()).thenReturn(false);when(f.getSize()).thenReturn(10L*1024*1024+1);assertThrows(BizException.class,()->service.upload(f,"urban",0));verifyNoInteractions(storage,mapper);}
 @Test void anonymousCannotUpload() {assertThrows(BizException.class,()->service.upload(null,"classic",0));verifyNoInteractions(storage,mapper);}
 @Test void metadataCompareAndSwapDoesNotOverwriteParallelEdits() {
  when(auth.getCurrentAdminUserId()).thenReturn(7L);when(adminMapper.lockBackground(9L)).thenReturn(bg());
  var ex=assertThrows(BizException.class,()->service.update(9L,new UpdateReq("urban",5,0,"urban",0,1)));
  assertEquals(409,ex.getCode());verify(adminMapper,never()).updateMetadata(anyLong(),anyString(),anyInt(),anyInt());
 }
 @Test void metadataOnlyNeverReplacesStoredImage() {
  when(auth.getCurrentAdminUserId()).thenReturn(7L);when(adminMapper.lockBackground(9L)).thenReturn(bg());when(adminMapper.updateMetadata(9L,"urban",5,0)).thenReturn(1);when(adminMapper.templateSceneCodeExists("urban")).thenReturn(1);when(storage.issueReadUrl(anyString(),any())).thenReturn("https://bucket/a.png?signed");
  var r=service.update(9L,new UpdateReq("urban",5,0,"classic",0,1));assertEquals(0,r.enabled());verify(storage,never()).upload(any(),any(),any());verify(audit).logRequired(any());
 }
 @Test void missingContextIsReadOnlyAndReturnsZeroRevision() {when(adminMapper.templateExists(3L)).thenReturn(1);assertEquals(0,service.editorBackground(3L).revision());verify(adminMapper,never()).initializeContext(anyLong(),anyLong());}
 @Test void backgroundContextRejectsDisabledItems() {when(auth.getCurrentAdminUserId()).thenReturn(7L);when(adminMapper.templateExists(3L)).thenReturn(1);var b=bg();b.setEnabled(0);when(adminMapper.lockBackground(9L)).thenReturn(b);assertThrows(BizException.class,()->service.saveEditorBackground(3L,new ContextSaveReq(0L,9L)));verify(adminMapper,never()).saveContext(anyLong(),anyLong(),any(),anyLong());}
 @Test void contextConflictDoesNotWriteOrGenerateAi() {when(auth.getCurrentAdminUserId()).thenReturn(7L);when(adminMapper.templateExists(3L)).thenReturn(1);var c=new ActorCardDecorationEditorContext();c.setRevision(2L);when(adminMapper.lockContext(3L)).thenReturn(c);var ex=assertThrows(BizException.class,()->service.saveEditorBackground(3L,new ContextSaveReq(0L,null)));assertEquals(409,ex.getCode());verify(adminMapper,never()).saveContext(anyLong(),anyLong(),any(),anyLong());verifyNoInteractions(storage);}
}
