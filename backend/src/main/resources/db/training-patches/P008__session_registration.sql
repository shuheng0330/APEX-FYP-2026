-- Session registration (FR-13): one registration per person per session, reused if they register again, and one
-- invitation per person per session (FR-12-14). The 15-minute job finds seat offers that have run out (FR-13-07).
-- The new registration columns (offer times, who cancelled and why, the HRDC details) are plain columns Hibernate adds.
CREATE UNIQUE INDEX IF NOT EXISTS uq_training_registration ON training_registration (training_id, staff_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_training_invitation ON training_invitation (training_id, staff_id);
CREATE INDEX IF NOT EXISTS idx_training_registration_offer ON training_registration (status, offer_expires_at);
