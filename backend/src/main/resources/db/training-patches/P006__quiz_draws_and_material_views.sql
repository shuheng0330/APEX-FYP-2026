-- Taking a quiz (FR-11-04, FR-11-14): an attempt's drawn questions and a learner's last look at a module's material
-- are each looked up by enrolment and module.
CREATE UNIQUE INDEX IF NOT EXISTS uq_sop_quiz_draw ON sop_quiz_draw (enrollment_id, sop_module_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sop_material_view ON sop_material_view (enrollment_id, sop_module_id);
