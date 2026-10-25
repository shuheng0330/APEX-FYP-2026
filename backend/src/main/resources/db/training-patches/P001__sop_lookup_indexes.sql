-- Indexes for the lookups the training services make. Hibernate creates the sop_* tables without them,
-- and PostgreSQL does not index these columns by itself. sop_enrollment and sop_course_feedback are
-- already covered by their (sop_document_id, staff_id) unique constraints when looked up by course.
CREATE INDEX IF NOT EXISTS idx_sop_module_document ON sop_module (sop_document_id, module_order);
CREATE INDEX IF NOT EXISTS idx_sop_quiz_question_module ON sop_quiz_question (sop_module_id, id);
CREATE INDEX IF NOT EXISTS idx_sop_quiz_review_event_module ON sop_quiz_review_event (sop_module_id);
CREATE INDEX IF NOT EXISTS idx_sop_assignment_document ON sop_assignment (sop_document_id);
CREATE INDEX IF NOT EXISTS idx_sop_enrollment_staff ON sop_enrollment (staff_id);
CREATE INDEX IF NOT EXISTS idx_sop_quiz_attempt_enrollment ON sop_quiz_attempt (enrollment_id, attempted_at);
