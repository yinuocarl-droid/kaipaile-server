-- B7 additive, repeatable. Does not alter old card/template/background rows.
CREATE TABLE IF NOT EXISTS actor_card_decoration_editor_context (
    template_id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    background_id BIGINT NULL,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_decoration_editor_revision CHECK(revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
