package com.kaipai.mapper.actor;
import com.kaipai.model.actor.card.entity.ActorCardBackground;
import com.kaipai.model.actor.card.entity.ActorCardDecorationEditorContext;
import org.apache.ibatis.annotations.*;
/** Administrative locks and editor context, isolated from old mini-program queries. */
@Mapper
public interface ActorCardBackgroundAdminMapper {
    @Select("SELECT * FROM actor_card_background WHERE id=#{id} FOR UPDATE")
    ActorCardBackground lockBackground(@Param("id") Long id);
    @Update("UPDATE actor_card_background SET style=#{style},sort_order=#{sort},enabled=#{enabled},last_update=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateMetadata(@Param("id") Long id, @Param("style") String style, @Param("sort") int sort, @Param("enabled") int enabled);
    @Select("SELECT COUNT(*) FROM card_scene_template WHERE template_id=#{id} AND deleted=0")
    int templateExists(@Param("id") Long id);
    @Select("SELECT * FROM actor_card_decoration_editor_context WHERE template_id=#{id}")
    ActorCardDecorationEditorContext context(@Param("id") Long id);
    @Select("SELECT * FROM actor_card_decoration_editor_context WHERE template_id=#{id} FOR UPDATE")
    ActorCardDecorationEditorContext lockContext(@Param("id") Long id);
    @Insert("INSERT INTO actor_card_decoration_editor_context(template_id,revision,updated_by) VALUES(#{id},0,#{admin}) ON DUPLICATE KEY UPDATE template_id=template_id")
    int initializeContext(@Param("id") Long id, @Param("admin") Long admin);
    @Update("UPDATE actor_card_decoration_editor_context SET background_id=#{background},revision=revision+1,updated_by=#{admin},updated_at=CURRENT_TIMESTAMP(6) WHERE template_id=#{id} AND revision=#{revision}")
    int saveContext(@Param("id") Long id, @Param("revision") Long revision, @Param("background") Long background, @Param("admin") Long admin);
}
