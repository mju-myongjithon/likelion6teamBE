BEGIN;

-- Hibernate ddl-auto=update does not widen an existing enum check constraint.
ALTER TABLE group_join_applications
    DROP CONSTRAINT IF EXISTS group_join_applications_status_check;
ALTER TABLE group_join_applications
    ADD CONSTRAINT group_join_applications_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_group_applications_applicant_requested
    ON group_join_applications (applicant_user_id, requested_at, group_join_application_id);

COMMIT;
