-- Add API_AND_DATABASE_DESIGN to the sessions type constraint.
-- The original inline CHECK on the type column must be replaced with a named
-- table-level constraint so it can be managed independently going forward.

-- Step 1: Drop the existing inline check constraint on the type column.
-- PostgreSQL auto-names inline column constraints as {table}_{column}_check.
ALTER TABLE sessions DROP CONSTRAINT IF EXISTS sessions_type_check;

-- Step 2: Add a named table-level constraint that includes the new value.
ALTER TABLE sessions ADD CONSTRAINT sessions_type_check
    CHECK (type IN ('DSA', 'HLD', 'LLD', 'RESUME_GRILLING', 'CULTURE_FIT', 'API_AND_DATABASE_DESIGN'));
