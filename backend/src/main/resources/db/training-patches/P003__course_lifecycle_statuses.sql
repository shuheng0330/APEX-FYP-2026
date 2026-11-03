-- Courses now have a lifecycle status, and enrolments can be Withdrawn (FR-06-14, FR-06-15, NFR-010).
-- Hibernate created these CHECK constraints from the enum values of the time and never updates them, so they
-- would reject the new values. The columns stay NOT NULL, and the application writes only known values.
ALTER TABLE sop_enrollment DROP CONSTRAINT IF EXISTS sop_enrollment_status_check;
ALTER TABLE sop_document DROP CONSTRAINT IF EXISTS sop_document_course_status_check;
