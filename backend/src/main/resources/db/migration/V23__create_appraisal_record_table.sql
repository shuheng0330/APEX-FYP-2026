CREATE TABLE appraisal_record
(
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    staff_id                   UUID        NOT NULL,
    manager_id                 UUID        NOT NULL,
    evaluation_cycle_id        BIGINT      NOT NULL,
    review_period_years        INTEGER     NOT NULL DEFAULT 1,

    decision_type              VARCHAR(20) NOT NULL,

    promotion_readiness_score  DECIMAL(5, 2),
    salary_readiness_score     DECIMAL(5, 2),

    promotion_system_category  VARCHAR(30),
    salary_system_category     VARCHAR(30),
    promotion_final_category   VARCHAR(30),
    salary_final_category      VARCHAR(30),

    promotion_override_reason  TEXT,
    salary_override_reason     TEXT,
    manager_comment            TEXT,
    ai_insight                 TEXT,

    status                     VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    hr_reviewer_id             UUID,
    hr_override_category       VARCHAR(30),
    hr_override_reason         TEXT,
    hr_return_reason           TEXT,

    submitted_at               TIMESTAMP,
    approved_at                TIMESTAMP,

    created_at                 TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by                 UUID,
    updated_by                 UUID,

    CONSTRAINT fk_appraisal_record_staff FOREIGN KEY (staff_id)
        REFERENCES staff (id),

    CONSTRAINT fk_appraisal_record_manager FOREIGN KEY (manager_id)
        REFERENCES staff (id),

    CONSTRAINT fk_appraisal_record_evaluation_cycle FOREIGN KEY (evaluation_cycle_id)
        REFERENCES evaluation_cycle (id),

    CONSTRAINT fk_appraisal_record_hr_reviewer FOREIGN KEY (hr_reviewer_id)
        REFERENCES staff (id),

    CONSTRAINT fk_appraisal_record_created_by FOREIGN KEY (created_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_appraisal_record_updated_by FOREIGN KEY (updated_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT uq_appraisal_record_staff_cycle
        UNIQUE (staff_id, evaluation_cycle_id),

    CONSTRAINT chk_appraisal_record_review_period
        CHECK (review_period_years IN (1, 3, 5)),

    CONSTRAINT chk_appraisal_record_decision_type
        CHECK (decision_type IN ('PROMOTION', 'SALARY_INCREMENT', 'BOTH')),

    CONSTRAINT chk_appraisal_record_status
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'RETURNED', 'APPROVED')),

    CONSTRAINT chk_appraisal_record_promotion_score
        CHECK (promotion_readiness_score IS NULL OR promotion_readiness_score BETWEEN 0 AND 100),

    CONSTRAINT chk_appraisal_record_salary_score
        CHECK (salary_readiness_score IS NULL OR salary_readiness_score BETWEEN 0 AND 100),

    CONSTRAINT chk_appraisal_record_promotion_system_category
        CHECK (promotion_system_category IS NULL OR promotion_system_category IN ('READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT')),

    CONSTRAINT chk_appraisal_record_salary_system_category
        CHECK (salary_system_category IS NULL OR salary_system_category IN ('READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT')),

    CONSTRAINT chk_appraisal_record_promotion_final_category
        CHECK (promotion_final_category IS NULL OR promotion_final_category IN ('READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT')),

    CONSTRAINT chk_appraisal_record_salary_final_category
        CHECK (salary_final_category IS NULL OR salary_final_category IN ('READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT')),

    CONSTRAINT chk_appraisal_record_hr_override_category
        CHECK (hr_override_category IS NULL OR hr_override_category IN ('READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT'))
);

CREATE INDEX idx_appraisal_record_status
    ON appraisal_record (status);

CREATE INDEX idx_appraisal_record_staff
    ON appraisal_record (staff_id);

CREATE INDEX idx_appraisal_record_manager
    ON appraisal_record (manager_id);

CREATE INDEX idx_appraisal_record_evaluation_cycle
    ON appraisal_record (evaluation_cycle_id);

CREATE INDEX idx_appraisal_record_status_cycle
    ON appraisal_record (status, evaluation_cycle_id);
