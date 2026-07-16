BEGIN;

-- Hibernate ddl-auto=update does not widen an existing enum check constraint.
ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_type_check;
ALTER TABLE tags ADD CONSTRAINT tags_type_check
    CHECK (type IN ('INTEREST', 'PURPOSE', 'ROLE', 'EVENT'));

COMMIT;
