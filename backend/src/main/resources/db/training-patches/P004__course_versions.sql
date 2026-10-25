-- Course versions (FR-06-06, FR-07-16 to FR-07-18): every version of a course shares its course id, the first
-- version's id. Courses made before versions are their own first version.
UPDATE sop_document SET course_id = id WHERE course_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_sop_document_course_id ON sop_document (course_id);
-- A draft's quiz history starts with "copied from v1". Hibernate's CHECK constraint on the action column lists
-- only the values of the time, so it would reject the new one.
ALTER TABLE sop_quiz_review_event DROP CONSTRAINT IF EXISTS sop_quiz_review_event_action_check;
