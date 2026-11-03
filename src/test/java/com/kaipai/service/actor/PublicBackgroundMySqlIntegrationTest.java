package com.kaipai.service.actor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.*;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.*;
import com.kaipai.model.actor.card.dto.AdminActorCardBackgroundDTO.*;
import com.kaipai.model.system.entity.AdminOperationLog;
import com.kaipai.service.system.AdminOperationLogService;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicBackgroundMySqlIntegrationTest {
 MySQLContainer<?> mysql;AnnotationConfigApplicationContext app;JdbcTemplate jdbc;
 static DataSource db;static AtomicBoolean failAudit=new AtomicBoolean();
 AdminActorCardBackgroundService admin;ActorCardBackgroundService mini;
 @BeforeAll void start() throws Exception {
  assertTrue(DockerClientFactory.instance().isDockerAvailable(),"isolated Docker MySQL required; no silent skips");
  mysql=new MySQLContainer<>("mysql:8.0.36").withDatabaseName("background_isolated");mysql.start();
  db=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());jdbc=new JdbcTemplate(db);
  jdbc.execute("CREATE TABLE card_scene_template(template_id BIGINT PRIMARY KEY,deleted INT DEFAULT 0)");jdbc.update("INSERT INTO card_scene_template VALUES(7,0)");
  jdbc.execute("CREATE TABLE actor_card_background(id BIGINT AUTO_INCREMENT PRIMARY KEY,style VARCHAR(20) NOT NULL,image_url VARCHAR(2048) NOT NULL,thumbnail_url VARCHAR(2048),sort_order INT NOT NULL DEFAULT 0,enabled INT NOT NULL DEFAULT 1,create_time DATETIME DEFAULT CURRENT_TIMESTAMP,last_update DATETIME DEFAULT CURRENT_TIMESTAMP)");
  jdbc.execute("CREATE TABLE background_test_audit(id BIGINT AUTO_INCREMENT PRIMARY KEY,target_id BIGINT)");migrate();migrate();
  app=new AnnotationConfigApplicationContext(Config.class);admin=app.getBean(AdminActorCardBackgroundService.class);mini=app.getBean(ActorCardBackgroundService.class);
 }
 void migrate() throws Exception {try(var c=db.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V20261023_001__decoration_editor_background.sql"));}}
 @AfterAll void stop(){if(app!=null)app.close();if(mysql!=null)mysql.stop();}
 @BeforeEach void reset(){failAudit.set(false);jdbc.update("DELETE FROM actor_card_decoration_editor_context");jdbc.update("DELETE FROM actor_card_background");jdbc.update("DELETE FROM background_test_audit");jdbc.update("INSERT INTO actor_card_background(id,style,image_url,sort_order,enabled) VALUES(2,'classic','https://bucket/2.png',3,1),(1,'classic','https://bucket/1.png',3,1),(3,'classic','https://bucket/3.png',1,0)");}
 @Test void sameCatalogUsesSameEnabledIdsOrderAndSignedImages() {
  var a=admin.list("classic",false,1,30);var m=mini.listByStyle("classic").getImages();
  assertEquals(java.util.List.of(1L,2L),a.stream().map(Item::id).toList());assertEquals(a.stream().map(Item::id).toList(),m.stream().map(x->x.getId()).toList());
  assertEquals(a.get(0).imageUrl(),m.get(0).getImageUrl());assertEquals(3,admin.list("classic",true,1,30).size());
  admin.update(1L,new UpdateReq("urban",8,0,"classic",3,1));assertEquals(java.util.List.of(2L),mini.listByStyle("classic").getImages().stream().map(x->x.getId()).toList());
  assertEquals("https://bucket/1.png",jdbc.queryForObject("SELECT image_url FROM actor_card_background WHERE id=1",String.class));
 }
 @Test void selectionSurvivesReloadAndRepeatedMigrationWithoutTouchingLayout() throws Exception {
  assertEquals(0,admin.editorBackground(7L).revision());var saved=admin.saveEditorBackground(7L,new ContextSaveReq(0L,1L));assertEquals(1,saved.revision());assertEquals(1L,saved.backgroundId());migrate();assertEquals(1L,admin.editorBackground(7L).backgroundId());
  assertEquals(2,mini.listByStyle("classic").getImages().size());
 }
 @Test void concurrentContextSaveHasExactlyOneWinner() throws Exception {
  var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  try {Callable<Boolean> save=()->{gate.await(10,TimeUnit.SECONDS);try{admin.saveEditorBackground(7L,new ContextSaveReq(0L,1L));return true;}catch(BizException e){assertEquals(409,e.getCode());return false;}};var a=pool.submit(save);var b=pool.submit(save);gate.countDown();assertNotEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));assertEquals(1,admin.editorBackground(7L).revision());}finally{pool.shutdownNow();}
 }
 @Test void requiredAuditFailureRollsBackContextAndMetadata() {
  failAudit.set(true);assertThrows(IllegalStateException.class,()->admin.saveEditorBackground(7L,new ContextSaveReq(0L,1L)));assertEquals(0,admin.editorBackground(7L).revision());
  assertThrows(IllegalStateException.class,()->admin.update(1L,new UpdateReq("urban",9,0,"classic",3,1)));assertEquals(2,mini.listByStyle("classic").getImages().size());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM background_test_audit",Integer.class));
 }
 @Test void disabledOldEditorBackgroundRemainsReadableButCannotBeNewlySelected() {
  admin.saveEditorBackground(7L,new ContextSaveReq(0L,1L));admin.update(1L,new UpdateReq("classic",3,0,"classic",3,1));assertEquals(0,admin.editorBackground(7L).background().enabled());assertThrows(BizException.class,()->admin.saveEditorBackground(7L,new ContextSaveReq(1L,1L)));assertEquals(1L,admin.editorBackground(7L).backgroundId());
 }
 @Configuration @EnableTransactionManagement(proxyTargetClass=true) static class Config {
  @Bean DataSource ds(){return db;}
  @Bean PlatformTransactionManager tx(DataSource ds){return new DataSourceTransactionManager(ds);}
  @Bean SqlSessionFactory factory(DataSource ds)throws Exception{var c=new MybatisConfiguration();c.setMapUnderscoreToCamelCase(true);c.addMapper(ActorCardBackgroundMapper.class);c.addMapper(ActorCardBackgroundAdminMapper.class);var f=new MybatisSqlSessionFactoryBean();f.setDataSource(ds);f.setConfiguration(c);return f.getObject();}
  @Bean ActorCardBackgroundMapper mapper(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(ActorCardBackgroundMapper.class);}
  @Bean ActorCardBackgroundAdminMapper adminMapper(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(ActorCardBackgroundAdminMapper.class);}
  @Bean AdminAuthContext auth(){var a=mock(AdminAuthContext.class);when(a.getCurrentAdminUserId()).thenReturn(7L);return a;}
  @Bean AiGeneratedImageStorage storage(){var s=mock(AiGeneratedImageStorage.class);when(s.issueReadUrl(anyString(),any())).thenAnswer(i->i.getArgument(0)+"?signed=1");return s;}
  @Bean AdminOperationLogService auditStore(DataSource ds){var s=mock(AdminOperationLogService.class);when(s.save(any(AdminOperationLog.class))).thenAnswer(i->{new JdbcTemplate(ds).update("INSERT INTO background_test_audit(target_id) VALUES(?)",i.getArgument(0,AdminOperationLog.class).getTargetId());return !failAudit.get();});return s;}
  @Bean AdminOperationLogger audit(AdminOperationLogService s,AdminAuthContext a){return new AdminOperationLogger(s,a,new ObjectMapper());}
  @Bean AdminActorCardBackgroundService admin(ActorCardBackgroundMapper m,ActorCardBackgroundAdminMapper a,AiGeneratedImageStorage s,AdminAuthContext auth,AdminOperationLogger audit){return new AdminActorCardBackgroundService(m,a,s,auth,audit);}
  @Bean ActorCardDecorationAssignmentService decorationAssignments(){return mock(ActorCardDecorationAssignmentService.class);}
  @Bean ActorCardBackgroundService mini(ActorCardBackgroundMapper m,AiGeneratedImageStorage s,ActorCardDecorationAssignmentService d){return new ActorCardBackgroundService(m,s,d);}
 }
}
