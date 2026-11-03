package com.kaipai.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class ActorProfileHeroBadgeMigrationTest {
    @Test void migrationRunsTwicePreservesRowsAndHandlesMissingBaseTable() throws Exception {
        var resource = new ClassPathResource("db/migration/V20261023_002__actor_profile_hero_badge_text.sql");
        assertTrue(resource.exists(), "B12 repeatable production pre-provision migration must exist");
        try (var mysql = new MySQLContainer<>("mysql:8.0.36")) {
            mysql.start();
            try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
                 var statement = connection.createStatement()) {
                ScriptUtils.executeSqlScript(connection,resource);
                statement.execute("CREATE TABLE actor_profile(actor_profile_id BIGINT PRIMARY KEY, nick_name VARCHAR(80))");
                statement.execute("INSERT INTO actor_profile VALUES(1,'original')");
                ScriptUtils.executeSqlScript(connection,resource);
                statement.execute("UPDATE actor_profile SET hero_badge_text='locked' WHERE actor_profile_id=1");
                ScriptUtils.executeSqlScript(connection,resource);
                try (var rs=statement.executeQuery("SELECT nick_name,hero_badge_text FROM actor_profile WHERE actor_profile_id=1")) {
                    assertTrue(rs.next()); assertEquals("original",rs.getString(1)); assertEquals("locked",rs.getString(2));
                }
                try (var rs=statement.executeQuery("SELECT CHARACTER_MAXIMUM_LENGTH,IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='actor_profile' AND column_name='hero_badge_text'")) {
                    assertTrue(rs.next()); assertEquals(80,rs.getInt(1)); assertEquals("YES",rs.getString(2));
                }
            }
        }
    }
}
