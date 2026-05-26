CREATE TABLE training_program_competency (
                                              training_id BIGINT NOT NULL,
                                              competency_id BIGINT NOT NULL,

                                              PRIMARY KEY (training_id, competency_id),
                                              CONSTRAINT training_program_competency_training_id_fkey
                                                  FOREIGN KEY (training_id)
                                                      REFERENCES training_program(training_id)
                                                      ON DELETE CASCADE,
                                              CONSTRAINT training_program_competency_competency_id_fkey
                                                  FOREIGN KEY (competency_id)
                                                      REFERENCES competency(id)
                                                      ON DELETE CASCADE
);

ALTER TABLE training_program ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;

UPDATE training_program
SET is_deleted = FALSE
WHERE is_deleted IS NULL;

ALTER TABLE training_program
ALTER COLUMN created_by TYPE UUID
USING created_by::uuid;

ALTER TABLE training_program
ALTER COLUMN updated_by TYPE UUID
USING updated_by::uuid;

-- 1. Add the invited_by column
ALTER TABLE training_invitation
ADD COLUMN invited_by UUID;

-- 2. Add foreign key constraint
ALTER TABLE training_invitation
ADD CONSTRAINT fk_training_invitation_invited_by
FOREIGN KEY (invited_by)
REFERENCES staff(id);
