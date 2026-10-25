-- Enrolments made before courses had an estimated duration take the default of 60 minutes (FR-07-15),
-- the value every such course counts as until its trainer sets one.
UPDATE sop_enrollment SET duration_minutes = 60 WHERE duration_minutes IS NULL;
