DROP TABLE learning_material_departments;

CREATE TABLE learning_material_org_chart
(
    material_id  BIGINT NOT NULL,
    org_chart_id BIGINT NOT NULL,
    PRIMARY KEY (material_id, org_chart_id),
    CONSTRAINT learning_material_org_chart_material_id_fkey
        FOREIGN KEY (material_id)
            REFERENCES learning_material (material_id)
            ON DELETE CASCADE,
    CONSTRAINT learning_material_org_chart_org_chart_id_fkey
        FOREIGN KEY (org_chart_id)
            REFERENCES org_chart (id)
);

ALTER TABLE learning_document
ADD COLUMN total_pages INTEGER,
ADD COLUMN total_duration DOUBLE PRECISION,
ADD COLUMN created_at TIMESTAMP,
ADD COLUMN file_type VARCHAR(255);

ALTER TABLE learning_material
DROP COLUMN department;

ALTER TABLE learning_document
DROP COLUMN order_index;

ALTER TABLE training_registration
DROP CONSTRAINT training_registration_training_id_fkey,
ADD CONSTRAINT training_registration_training_id_fkey
FOREIGN KEY (training_id)
REFERENCES training_program(training_id)
ON DELETE CASCADE;

ALTER TABLE training_program_org_chart
DROP CONSTRAINT training_program_org_chart_training_id_fkey,
ADD CONSTRAINT training_program_org_chart_training_id_fkey
FOREIGN KEY (training_id)
REFERENCES training_program(training_id)
ON DELETE CASCADE;

DROP TABLE learning_material_competency;

CREATE TABLE learning_material_competency (
                                              material_id BIGINT NOT NULL,
                                              competency_id BIGINT NOT NULL,

                                              PRIMARY KEY (material_id, competency_id),
                                              CONSTRAINT learning_material_competency_material_id_fkey
                                                  FOREIGN KEY (material_id)
                                                      REFERENCES learning_material(material_id)
                                                      ON DELETE CASCADE,
                                              CONSTRAINT learning_material_competency_competency_id_fkey
                                                  FOREIGN KEY (competency_id)
                                                      REFERENCES competency(id)
                                                      ON DELETE CASCADE
);

ALTER TABLE staff_learning_document_progress
ADD COLUMN  is_completed BOOLEAN DEFAULT FALSE;
