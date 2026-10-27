-- Training sessions (FR-12), rebuilt on the old Training Programme tables: a session has a lifecycle (Scheduled,
-- Cancelled, Archived), a kind, a delivery and a training type; a registration has a status (NFR-010); an invitation
-- can be declined. Hibernate created CHECK constraints from the enum values of the time and never updates them, so
-- they would reject the new values (as in P003). The application writes only known values.
ALTER TABLE training_program DROP CONSTRAINT IF EXISTS training_program_status_check;
ALTER TABLE training_program DROP CONSTRAINT IF EXISTS training_program_session_kind_check;
ALTER TABLE training_program DROP CONSTRAINT IF EXISTS training_program_delivery_check;
ALTER TABLE training_program DROP CONSTRAINT IF EXISTS training_program_training_type_check;
ALTER TABLE training_registration DROP CONSTRAINT IF EXISTS training_registration_status_check;
ALTER TABLE training_invitation DROP CONSTRAINT IF EXISTS training_invitation_status_check;

-- Rows from the old pages, if a database has any, map onto the new statuses and flags.
UPDATE training_program SET status = 'SCHEDULED' WHERE status IS NULL OR status IN ('UPCOMING', 'ONGOING', 'COMPLETED');
UPDATE training_program SET is_deleted = false WHERE is_deleted IS NULL;
UPDATE training_program SET is_mandatory = false WHERE is_mandatory IS NULL;
UPDATE training_invitation SET status = 'DECLINED' WHERE status = 'REJECTED';
UPDATE training_invitation SET status = 'PENDING' WHERE status IS NULL;

-- Longer descriptions, and online meeting links as the venue.
ALTER TABLE training_program ALTER COLUMN description TYPE text;
ALTER TABLE training_program ALTER COLUMN venue TYPE text;

-- Job roles in a session's audience are now a plain list; mandatory is set per session (FR-12-03), so the old
-- per-role flag is no longer written and defaults to false.
ALTER TABLE training_target_role ADD COLUMN IF NOT EXISTS is_mandatory boolean NOT NULL DEFAULT false;
ALTER TABLE training_target_role ALTER COLUMN is_mandatory SET DEFAULT false;

-- The calendar looks sessions up by date (FR-12-11), and counts each session's seats (FR-13-04).
CREATE INDEX IF NOT EXISTS idx_training_program_date ON training_program (start_date);
CREATE INDEX IF NOT EXISTS idx_training_registration_session ON training_registration (training_id, status);
