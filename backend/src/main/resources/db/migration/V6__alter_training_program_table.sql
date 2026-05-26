CREATE TABLE training_program_org_chart (
                                            training_id BIGINT NOT NULL,
                                            org_chart_id BIGINT NOT NULL,
                                            PRIMARY KEY (training_id, org_chart_id),
                                            FOREIGN KEY (training_id) REFERENCES training_program(training_id),
                                            FOREIGN KEY (org_chart_id) REFERENCES org_chart(id)
);
ALTER TABLE training_invitation
    ADD COLUMN respond_at TIMESTAMP;

CREATE TABLE staff_learning_material (

        enrollment_id BIGSERIAL PRIMARY KEY,
        staff_id UUID NOT NULL,
        material_id BIGINT NOT NULL,
        enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        completed_at TIMESTAMP,
        progress DECIMAL(5,2) DEFAULT 0.00,
        is_completed BOOLEAN DEFAULT FALSE,
        enrolled_by UUID,

        CONSTRAINT fk_staff
            FOREIGN KEY (staff_id)
                REFERENCES staff (id)
                ON DELETE CASCADE,

        CONSTRAINT fk_learning_material
            FOREIGN KEY (material_id)
                REFERENCES learning_material (material_id)
                ON DELETE CASCADE
);

CREATE TABLE staff_learning_document_progress (

    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGSERIAL NOT NULL,
    document_id BIGINT NOT NULL,
    last_position VARCHAR(255),
    last_accessed_at TIMESTAMP,

    CONSTRAINT fk_enrollment
        FOREIGN KEY (enrollment_id)
            REFERENCES staff_learning_material(enrollment_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_document
        FOREIGN KEY (document_id)
            REFERENCES learning_document(document_id)
            ON DELETE CASCADE

)