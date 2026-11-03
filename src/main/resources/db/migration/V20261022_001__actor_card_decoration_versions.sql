-- 00-264 B2a: additive, repeatable provisioning. No business data is updated or seeded.
CREATE TABLE IF NOT EXISTS actor_card_decoration_draft (
    template_id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    schema_json MEDIUMTEXT NOT NULL,
    last_version_no INT NOT NULL DEFAULT 0,
    active_version_no INT NULL,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_decoration_revision CHECK (revision >= 0),
    CONSTRAINT chk_decoration_head CHECK (last_version_no >= 0 AND
        (active_version_no IS NULL OR (active_version_no > 0 AND active_version_no <= last_version_no)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS actor_card_decoration_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    schema_json MEDIUMTEXT NOT NULL,
    checksum CHAR(64) NOT NULL,
    published_by BIGINT NOT NULL,
    publish_note VARCHAR(200) NOT NULL,
    published_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_decoration_version (template_id, version_no),
    CONSTRAINT chk_decoration_version CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
