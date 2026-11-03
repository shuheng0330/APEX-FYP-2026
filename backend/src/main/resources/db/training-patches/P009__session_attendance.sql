-- Attendance (FR-14): each row now has a status (Attended or Absent; no row means Not Recorded) and a method (manual
-- or batch). Hibernate adds CHECK constraints for these enum columns and never updates them, so they are dropped, as in
-- P003 and P007. One attendance row per person per session.
ALTER TABLE training_attendance DROP CONSTRAINT IF EXISTS training_attendance_status_check;
ALTER TABLE training_attendance DROP CONSTRAINT IF EXISTS training_attendance_method_check;
CREATE UNIQUE INDEX IF NOT EXISTS uq_training_attendance ON training_attendance (training_id, staff_id);
