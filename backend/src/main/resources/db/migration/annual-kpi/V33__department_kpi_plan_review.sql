-- Review decisions apply to the whole plan; retain latest submission and decision metadata.
-- Development Hibernate may have created these seven nullable columns before the explicit runner.
DO $$
DECLARE existing_count INTEGER; compatible_count INTEGER;
BEGIN
    SELECT count(*), count(*) FILTER (WHERE is_nullable='YES' AND column_default IS NULL AND
        udt_name=CASE column_name
            WHEN 'submitted_at' THEN 'timestamptz' WHEN 'reviewed_at' THEN 'timestamptz'
            WHEN 'submitted_by' THEN 'uuid' WHEN 'reviewed_by' THEN 'uuid'
            WHEN 'return_reason' THEN 'text' ELSE 'bool' END)
    INTO existing_count,compatible_count FROM information_schema.columns
    WHERE table_schema=current_schema() AND table_name='kpi_plan' AND column_name IN
        ('submitted_at','submitted_by','submitted_late','reviewed_at','reviewed_by','reviewed_late','return_reason');
    IF existing_count NOT IN (0,7) OR compatible_count<>existing_count THEN
        RAISE EXCEPTION 'Incompatible or partial untracked Department review columns; reconcile explicitly';
    END IF;
END $$;
ALTER TABLE kpi_plan ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ;
ALTER TABLE kpi_plan ADD COLUMN IF NOT EXISTS submitted_by UUID;
ALTER TABLE kpi_plan ADD COLUMN IF NOT EXISTS submitted_late BOOLEAN;
ALTER TABLE kpi_plan ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
ALTER TABLE kpi_plan ADD COLUMN IF NOT EXISTS reviewed_by UUID;
ALTER TABLE kpi_plan ADD COLUMN IF NOT EXISTS reviewed_late BOOLEAN;
ALTER TABLE kpi_plan ADD COLUMN IF NOT EXISTS return_reason TEXT;
ALTER TABLE kpi_plan ADD CONSTRAINT fk_kpi_plan_submitter FOREIGN KEY (submitted_by) REFERENCES staff(id);
ALTER TABLE kpi_plan ADD CONSTRAINT fk_kpi_plan_reviewer FOREIGN KEY (reviewed_by) REFERENCES staff(id);
ALTER TABLE kpi_plan ADD CONSTRAINT ck_department_submission_metadata CHECK
    (level <> 'DEPARTMENT' OR status NOT IN ('PENDING_APPROVAL','RETURNED','APPROVED') OR
        (submitted_at IS NOT NULL AND submitted_by IS NOT NULL AND submitted_late IS NOT NULL));
ALTER TABLE kpi_plan ADD CONSTRAINT ck_department_review_metadata CHECK
    (level <> 'DEPARTMENT' OR status NOT IN ('RETURNED','APPROVED') OR
        (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL AND reviewed_late IS NOT NULL));
ALTER TABLE kpi_plan ADD CONSTRAINT ck_department_return_reason CHECK
    (level <> 'DEPARTMENT' OR status <> 'RETURNED' OR
        (return_reason IS NOT NULL AND length(trim(return_reason)) BETWEEN 1 AND 10000));
CREATE INDEX idx_department_kpi_review_queue ON kpi_plan(status,submitted_at,id) WHERE level='DEPARTMENT';
ALTER TABLE authority DROP CONSTRAINT authority_name_check;
ALTER TABLE authority ADD CONSTRAINT authority_name_check CHECK (name IN (
    'ROLE_USER','CAN_VIEW_ACCESS_CONTROL','CAN_MANAGE_ACCESS_CONTROL','CAN_VIEW_STAFF','CAN_MANAGE_STAFF',
    'CAN_VIEW_INVISIBLE_ROLE','CAN_MANAGE_ROLE','CAN_MANAGE_COMPETENCY','CAN_PROPOSE_ROLE_COMPETENCIES',
    'CAN_MANAGE_CAREER_PATHWAY','CAN_MANAGE_ORG_CHART','CAN_MANAGE_TRAINING','CAN_ASSIGN_TRAINING',
    'CAN_MANAGE_LEARNING_MATERIAL','CAN_MANAGE_EVALUATION','CAN_MANAGE_EVALUATION_CYCLE',
    'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD','CAN_MANAGE_COMPANY_KPI',
    'CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI'));
INSERT INTO authority(name,description_key,label_key) VALUES
    ('CAN_MANAGE_DEPARTMENT_KPI','auth.can.manage.department.kpi.desc','auth.can.manage.department.kpi'),
    ('CAN_APPROVE_DEPARTMENT_KPI','auth.can.approve.department.kpi.desc','auth.can.approve.department.kpi');
-- HOD/MD grants must be provisioned explicitly through existing RBAC; do not infer from job titles.
