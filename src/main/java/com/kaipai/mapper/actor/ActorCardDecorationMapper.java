package com.kaipai.mapper.actor;

import com.kaipai.model.actor.card.entity.ActorCardDecorationDraft;
import com.kaipai.model.actor.card.entity.ActorCardDecorationVersion;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Parameterized persistence operations; no update/delete operation exists for snapshots. */
@Mapper
public interface ActorCardDecorationMapper {
    @Select("SELECT COUNT(*) FROM card_scene_template WHERE template_id = #{id} AND deleted = 0")
    int templateExists(@Param("id") Long id);

    @Select("SELECT * FROM actor_card_decoration_draft WHERE template_id = #{id}")
    ActorCardDecorationDraft draft(@Param("id") Long id);

    @Select("SELECT * FROM actor_card_decoration_draft WHERE template_id = #{id} FOR UPDATE")
    ActorCardDecorationDraft lockDraft(@Param("id") Long id);

    @Insert("""
        INSERT INTO actor_card_decoration_draft
          (template_id, revision, schema_json, last_version_no, updated_by)
        VALUES (#{id}, 0, #{json}, 0, #{admin})
        ON DUPLICATE KEY UPDATE template_id = template_id
        """)
    int initialize(@Param("id") Long id, @Param("json") String json, @Param("admin") Long admin);

    @Update("""
        UPDATE actor_card_decoration_draft SET schema_json = #{json}, revision = revision + 1,
          updated_by = #{admin}, updated_at = CURRENT_TIMESTAMP(6)
        WHERE template_id = #{id} AND revision = #{revision}
        """)
    int saveDraft(@Param("id") Long id, @Param("revision") long revision,
                  @Param("json") String json, @Param("admin") Long admin);

    @Insert("""
        INSERT INTO actor_card_decoration_version
          (template_id, version_no, schema_json, checksum, published_by, publish_note)
        VALUES (#{templateId}, #{versionNo}, #{schemaJson}, #{checksum}, #{publishedBy}, #{publishNote})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertVersion(ActorCardDecorationVersion version);

    @Update("""
        UPDATE actor_card_decoration_draft SET active_version_no = #{version}, last_version_no = #{version},
          revision = revision + 1, updated_by = #{admin}, updated_at = CURRENT_TIMESTAMP(6)
        WHERE template_id = #{id} AND revision = #{revision}
        """)
    int publish(@Param("id") Long id, @Param("revision") long revision,
                @Param("version") int version, @Param("admin") Long admin);

    @Update("""
        UPDATE actor_card_decoration_draft SET active_version_no = #{version},
          revision = revision + 1, updated_by = #{admin}, updated_at = CURRENT_TIMESTAMP(6)
        WHERE template_id = #{id} AND revision = #{revision}
        """)
    int activate(@Param("id") Long id, @Param("revision") long revision,
                 @Param("version") int version, @Param("admin") Long admin);

    @Select("SELECT * FROM actor_card_decoration_version WHERE template_id = #{id} AND version_no = #{version}")
    ActorCardDecorationVersion version(@Param("id") Long id, @Param("version") int version);

    @Select("SELECT * FROM actor_card_decoration_version WHERE template_id = #{id} ORDER BY version_no DESC LIMIT 50")
    List<ActorCardDecorationVersion> versions(@Param("id") Long id);
}
