-- Baseline schema: captures the existing tables as-is.
-- Flyway will mark this as applied on first run (baselineOnMigrate=true).

CREATE TABLE IF NOT EXISTS sessions (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ,
    user_id     VARCHAR(255),
    type        VARCHAR(255) CHECK (type IN ('DSA', 'HLD', 'LLD', 'RESUME_GRILLING', 'CULTURE_FIT')),
    role        VARCHAR(255) CHECK (role IN ('SDE1', 'SDE2', 'SDE3')),
    status      VARCHAR(255) CHECK (status IN ('CREATED', 'ACTIVE', 'COMPLETED')),
    resume_text TEXT
);

CREATE TABLE IF NOT EXISTS messages (
    id          UUID PRIMARY KEY,
    session_id  UUID,
    role        VARCHAR(255) CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content     TEXT,
    created_at  TIMESTAMPTZ
);
