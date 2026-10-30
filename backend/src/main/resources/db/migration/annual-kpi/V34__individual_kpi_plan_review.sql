-- Individual plans reuse whole-plan submission/review fields and keep the superior chosen at submission.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM kpi_plan WHERE level='INDIVIDUAL' AND status <> 'DRAFT') THEN
        RAISE EXCEPTION 'Existing non-draft Individual KPI plans need explicit review routing reconciliation';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
        AND table_name='kpi_plan' AND column_name='submitted_to_superior_id'
        AND (udt_name <> 'uuid' OR is_nullable <> 'YES' OR column_default IS NOT NULL)) THEN
        RAISE EXCEPTION 'Incompatible untracked Individual KPI review routing column';
    END IF;
END $$;
ALTER TABLE kpi_plan ADD COLUMN IF NOT EXISTS submitted_to_superior_id UUID;
ALTER TABLE kpi_plan ADD CONSTRAINT fk_individual_kpi_superior
    FOREIGN KEY (submitted_to_superior_id) REFERENCES staff(id);
ALTER TABLE kpi_plan ADD CONSTRAINT ck_individual_submission_metadata CHECK
    (level <> 'INDIVIDUAL' OR status = 'DRAFT' OR
        (submitted_at IS NOT NULL AND submitted_by IS NOT NULL AND submitted_late IS NOT NULL
         AND submitted_to_superior_id IS NOT NULL));
ALTER TABLE kpi_plan ADD CONSTRAINT ck_individual_review_metadata CHECK
    (level <> 'INDIVIDUAL' OR status NOT IN ('RETURNED','APPROVED') OR
        (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL AND reviewed_late IS NOT NULL));
ALTER TABLE kpi_plan ADD CONSTRAINT ck_individual_return_reason CHECK
    (level <> 'INDIVIDUAL' OR status <> 'RETURNED' OR
        (return_reason IS NOT NULL AND length(trim(return_reason)) BETWEEN 1 AND 10000));
CREATE INDEX idx_individual_kpi_review_queue
    ON kpi_plan(submitted_to_superior_id,status,submitted_at,id) WHERE level='INDIVIDUAL';
ALTER TABLE authority DROP CONSTRAINT authority_name_check;
ALTER TABLE authority ADD CONSTRAINT authority_name_check CHECK (name IN (
    'ROLE_USER','CAN_VIEW_ACCESS_CONTROL','CAN_MANAGE_ACCESS_CONTROL','CAN_VIEW_STAFF','CAN_MANAGE_STAFF',
    'CAN_VIEW_INVISIBLE_ROLE','CAN_MANAGE_ROLE','CAN_MANAGE_COMPETENCY','CAN_PROPOSE_ROLE_COMPETENCIES',
    'CAN_MANAGE_CAREER_PATHWAY','CAN_MANAGE_ORG_CHART','CAN_MANAGE_TRAINING','CAN_ASSIGN_TRAINING',
    'CAN_MANAGE_LEARNING_MATERIAL','CAN_MANAGE_EVALUATION','CAN_MANAGE_EVALUATION_CYCLE',
    'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD','CAN_MANAGE_COMPANY_KPI',
    'CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI','CAN_REVIEW_INDIVIDUAL_KPI'));
INSERT INTO authority(name,description_key,label_key) VALUES
    ('CAN_REVIEW_INDIVIDUAL_KPI','auth.can.review.individual.kpi.desc','auth.can.review.individual.kpi');
-- Grant this authority to verified superior Roles through existing RBAC; do not infer it from titles.
