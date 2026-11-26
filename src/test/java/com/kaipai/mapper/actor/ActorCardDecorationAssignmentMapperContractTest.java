package com.kaipai.mapper.actor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActorCardDecorationAssignmentMapperContractTest {

    @Test
    void readinessBatchQueriesRegisterAsReadOnlyDynamicStatements() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(ActorCardDecorationAssignmentMapper.class);

        String bindings = sql(configuration, "backgroundBindings", Map.of("sourceIds", List.of(13L, 14L)));
        assertTrue(bindings.contains("actor_card_decoration_binding"));
        assertTrue(bindings.contains("source_id IN ( ? , ? )"));
        assertFalse(bindings.contains("FOR UPDATE"));

        String versions = sql(configuration, "publishedVersions", Map.of("versionIds", List.of(1L, 3L)));
        assertTrue(versions.contains("actor_card_decoration_version"));
        assertTrue(versions.contains("card_scene_template"));
        assertTrue(versions.contains("v.id IN ( ? , ? )"));
        assertFalse(versions.contains("FOR UPDATE"));
    }

    private String sql(MybatisConfiguration configuration, String method, Map<String, Object> parameters) {
        return configuration.getMappedStatement(ActorCardDecorationAssignmentMapper.class.getName() + "." + method)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
    }
}
