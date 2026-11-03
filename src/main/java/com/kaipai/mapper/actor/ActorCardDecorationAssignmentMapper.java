package com.kaipai.mapper.actor;

import com.kaipai.model.actor.card.entity.ActorCardDecorationAssignment;
import com.kaipai.model.actor.card.entity.ActorCardDecorationAssignmentAudit;
import com.kaipai.model.actor.card.entity.ActorCardDecorationBinding;
import com.kaipai.model.actor.card.entity.ActorCardDecorationVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** Persistence boundary for platform bindings and per-card immutable decoration references. */
@Mapper
public interface ActorCardDecorationAssignmentMapper {
    @Select("SELECT COUNT(*) FROM actor_card WHERE id = #{cardId} AND user_id = #{userId} AND deleted = 0")
    int countOwnedCard(@Param("userId") Long userId, @Param("cardId") Long cardId);

    @Select("""
            SELECT id FROM actor_card
            WHERE id = #{cardId} AND user_id = #{userId} AND deleted = 0
            FOR UPDATE
            """)
    Long lockOwnedCard(@Param("userId") Long userId, @Param("cardId") Long cardId);

    @Select("SELECT COUNT(*) FROM actor_card WHERE id = #{cardId} AND deleted = 0")
    int countCard(@Param("cardId") Long cardId);

    @Select("SELECT COUNT(*) FROM actor_card_background WHERE id = #{backgroundId} AND enabled = 1")
    int countEnabledBackground(@Param("backgroundId") Long backgroundId);

    @Select("""
            SELECT v.* FROM actor_card_decoration_version v
            JOIN card_scene_template t ON t.template_id = v.template_id AND t.deleted = 0
            WHERE v.id = #{versionId}
            """)
    ActorCardDecorationVersion publishedVersion(@Param("versionId") Long versionId);

    @Select("SELECT * FROM actor_card_decoration_binding WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    ActorCardDecorationBinding binding(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    @Select("""
            <script>
            SELECT * FROM actor_card_decoration_binding
            WHERE source_type = 'background' AND source_id IN
            <foreach collection="sourceIds" item="sourceId" open="(" separator="," close=")">
              #{sourceId}
            </foreach>
            </script>
            """)
    List<ActorCardDecorationBinding> backgroundBindings(@Param("sourceIds") List<Long> sourceIds);

    @Select("""
            <script>
            SELECT v.* FROM actor_card_decoration_version v
            JOIN card_scene_template t ON t.template_id = v.template_id AND t.deleted = 0
            WHERE v.id IN
            <foreach collection="versionIds" item="versionId" open="(" separator="," close=")">
              #{versionId}
            </foreach>
            </script>
            """)
    List<ActorCardDecorationVersion> publishedVersions(@Param("versionIds") List<Long> versionIds);

    @Select("SELECT * FROM actor_card_decoration_binding WHERE source_type = #{sourceType} AND source_id = #{sourceId} FOR UPDATE")
    ActorCardDecorationBinding bindingForUpdate(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    @Insert("""
            INSERT INTO actor_card_decoration_binding
              (source_type, source_id, revision, template_id, version_id, template_version, updated_by)
            VALUES (#{sourceType}, #{sourceId}, #{revision}, #{templateId}, #{versionId}, #{templateVersion}, #{updatedBy})
            """)
    int insertBinding(ActorCardDecorationBinding binding);

    @Update("""
            UPDATE actor_card_decoration_binding
            SET revision = revision + 1, template_id = #{templateId}, version_id = #{versionId},
                template_version = #{templateVersion}, updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP(6)
            WHERE source_type = #{sourceType} AND source_id = #{sourceId} AND revision = #{expectedRevision}
            """)
    int updateBinding(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId,
                      @Param("expectedRevision") long expectedRevision, @Param("templateId") Long templateId,
                      @Param("versionId") Long versionId, @Param("templateVersion") int templateVersion,
                      @Param("updatedBy") Long updatedBy);

    @Select("SELECT * FROM actor_card_decoration_assignment WHERE card_id = #{cardId}")
    ActorCardDecorationAssignment assignment(@Param("cardId") Long cardId);

    @Select("SELECT * FROM actor_card_decoration_assignment WHERE card_id = #{cardId} FOR UPDATE")
    ActorCardDecorationAssignment assignmentForUpdate(@Param("cardId") Long cardId);

    @Insert("""
            INSERT INTO actor_card_decoration_assignment
              (card_id, generation_mode, background_id, template_id, version_id, template_version, revision, assigned_by)
            VALUES (#{cardId}, #{generationMode}, #{backgroundId}, #{templateId}, #{versionId},
                    #{templateVersion}, #{revision}, #{assignedBy})
            """)
    int insertAssignment(ActorCardDecorationAssignment assignment);

    @Update("""
            UPDATE actor_card_decoration_assignment
            SET generation_mode = #{generationMode}, background_id = #{backgroundId}, template_id = #{templateId},
                version_id = #{versionId}, template_version = #{templateVersion}, revision = revision + 1,
                assigned_by = #{assignedBy}, updated_at = CURRENT_TIMESTAMP(6)
            WHERE card_id = #{cardId} AND revision = #{expectedRevision}
            """)
    int updateAssignmentSource(@Param("cardId") Long cardId, @Param("expectedRevision") long expectedRevision,
                               @Param("generationMode") String generationMode, @Param("backgroundId") Long backgroundId,
                               @Param("templateId") Long templateId, @Param("versionId") Long versionId,
                               @Param("templateVersion") int templateVersion, @Param("assignedBy") Long assignedBy);

    @Update("""
            UPDATE actor_card_decoration_assignment
            SET template_id = #{templateId}, version_id = #{versionId}, template_version = #{templateVersion},
                revision = revision + 1, assigned_by = #{assignedBy}, updated_at = CURRENT_TIMESTAMP(6)
            WHERE card_id = #{cardId} AND version_id = #{expectedVersionId}
            """)
    int updateAssignmentVersion(@Param("cardId") Long cardId, @Param("expectedVersionId") Long expectedVersionId,
                                @Param("templateId") Long templateId, @Param("versionId") Long versionId,
                                @Param("templateVersion") int templateVersion, @Param("assignedBy") Long assignedBy);

    @Insert("""
            INSERT INTO actor_card_decoration_assignment_audit
              (card_id, actor_user_id, operation, generation_mode, background_id, from_version_id, to_version_id)
            VALUES (#{cardId}, #{actorUserId}, #{operation}, #{generationMode}, #{backgroundId},
                    #{fromVersionId}, #{toVersionId})
            """)
    int insertAssignmentAudit(ActorCardDecorationAssignmentAudit audit);
}
