-- 00-264 B2b: additive and repeatable. Keeps old actor_card selects compatible by using only new tables.
-- No actor-card business row is migrated or rewritten; old cards therefore remain explicit legacy rows.

CREATE TABLE IF NOT EXISTS actor_card_decoration_binding (
    source_type VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    template_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    template_version INT NOT NULL,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (source_type, source_id),
    KEY idx_decoration_binding_version (version_id),
    CONSTRAINT chk_decoration_binding_source CHECK (
        (source_type = 'default' AND source_id = 0)
        OR (source_type = 'background' AND source_id > 0)
    ),
    CONSTRAINT chk_decoration_binding_revision CHECK (revision > 0),
    CONSTRAINT chk_decoration_binding_template_version CHECK (template_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS actor_card_decoration_assignment (
    card_id BIGINT NOT NULL PRIMARY KEY,
    generation_mode VARCHAR(32) NOT NULL,
    background_id BIGINT NULL,
    template_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    template_version INT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    assigned_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_decoration_assignment_version (version_id),
    KEY idx_decoration_assignment_background (background_id),
    CONSTRAINT chk_decoration_assignment_source CHECK (
        (generation_mode = 'person_ai' AND background_id IS NULL)
        OR (generation_mode = 'person_background_ai' AND background_id > 0)
    ),
    CONSTRAINT chk_decoration_assignment_revision CHECK (revision > 0),
    CONSTRAINT chk_decoration_assignment_template_version CHECK (template_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS actor_card_decoration_assignment_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    card_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    generation_mode VARCHAR(32) NOT NULL,
    background_id BIGINT NULL,
    from_version_id BIGINT NULL,
    to_version_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_decoration_assignment_audit_card (card_id, created_at),
    KEY idx_decoration_assignment_audit_version (to_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
