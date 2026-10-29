-- Session templates (FR-12-08 to FR-12-10): Hibernate creates the table. Each person's templates have different names,
-- whatever their case; two trainers may each have a template of the same name, as they see only their own.
CREATE UNIQUE INDEX IF NOT EXISTS uq_training_session_template_name ON training_session_template (created_by, lower(name));
