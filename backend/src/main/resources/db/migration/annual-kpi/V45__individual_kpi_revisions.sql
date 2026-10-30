-- Extend existing immutable versions; existing plans, assignments and assessments stay unchanged.
ALTER TABLE kpi_plan_version
    ADD COLUMN IF NOT EXISTS preparation_mode VARCHAR(20),
    ADD COLUMN IF NOT EXISTS revision_superior_id UUID;
ALTER TABLE kpi_plan_version ADD CONSTRAINT fk_individual_revision_superior
    FOREIGN KEY(revision_superior_id) REFERENCES staff(id);

-- V44 used unnamed checks. Resolve only the two checks being extended by their definitions.
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
        WHERE conrelid='kpi_plan_version'::regclass AND contype='c'
        AND (pg_get_constraintdef(oid) LIKE '%base_version_id IS NULL%'
            OR pg_get_constraintdef(oid) LIKE '%cancelled_from_state%')
    LOOP
        EXECUTE format('ALTER TABLE kpi_plan_version DROP CONSTRAINT %I',c.conname);
    END LOOP;
END $$;

ALTER TABLE kpi_plan_version
    ADD CONSTRAINT ck_kpi_revision_base CHECK (
        (version_number=1 AND base_version_id IS NULL AND revision_reason IS NULL) OR
        (version_number>1 AND base_version_id IS NOT NULL
            AND level IN ('COMPANY','DEPARTMENT','INDIVIDUAL')
            AND revision_reason IS NOT NULL AND length(trim(revision_reason)) BETWEEN 1 AND 10000)),
    ADD CONSTRAINT ck_kpi_revision_cancellation CHECK (
        (status='CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL
            AND cancellation_reason IS NOT NULL AND length(trim(cancellation_reason)) BETWEEN 1 AND 10000
            AND cancelled_from_state IS NOT NULL AND cancelled_from_state IN
                ('REQUESTED','DRAFT','RETURNED','PENDING_APPROVAL','PENDING_MD_APPROVAL','PENDING_HOD_CONFIRMATION')) OR
        (status<>'CANCELLED' AND cancelled_by IS NULL AND cancelled_at IS NULL
            AND cancellation_reason IS NULL AND cancelled_from_state IS NULL)),
    ADD CONSTRAINT ck_individual_revision_scope CHECK (
        CASE WHEN version_number>1 AND level='INDIVIDUAL' THEN
            preparation_mode IS NOT NULL AND preparation_mode IN ('SUPERIOR','HR_MD')
            AND revision_superior_id IS NOT NULL AND requested_by IS NOT NULL
            AND status IN ('REQUESTED','DRAFT','PENDING_APPROVAL','RETURNED','EFFECTIVE','SUPERSEDED','CANCELLED')
            AND (preparation_mode<>'HR_MD' OR requested_by<>revision_superior_id)
            AND (prepared_by IS NULL OR (prepared_at IS NOT NULL AND prepared_by=
                CASE preparation_mode WHEN 'SUPERIOR' THEN revision_superior_id ELSE requested_by END))
            AND (status NOT IN ('DRAFT','PENDING_APPROVAL','RETURNED','EFFECTIVE','SUPERSEDED') OR prepared_by IS NOT NULL)
            AND (status NOT IN ('PENDING_APPROVAL','RETURNED') OR
                (preparation_mode='HR_MD' AND submitted_at IS NOT NULL))
            AND (approved_by IS NULL OR approved_by=revision_superior_id)
            AND (status NOT IN ('EFFECTIVE','SUPERSEDED') OR
                (approved_by IS NOT NULL AND approved_at IS NOT NULL AND effective_at IS NOT NULL
                    AND confirmed_by IS NOT NULL AND confirmed_by=revision_superior_id AND confirmed_at IS NOT NULL))
        ELSE preparation_mode IS NULL AND revision_superior_id IS NULL END);

ALTER TABLE authority DROP CONSTRAINT authority_name_check;
ALTER TABLE authority ADD CONSTRAINT authority_name_check CHECK(name IN (
    'ROLE_USER','CAN_VIEW_ACCESS_CONTROL','CAN_MANAGE_ACCESS_CONTROL','CAN_VIEW_STAFF','CAN_MANAGE_STAFF',
    'CAN_VIEW_INVISIBLE_ROLE','CAN_MANAGE_ROLE','CAN_MANAGE_COMPETENCY','CAN_PROPOSE_ROLE_COMPETENCIES',
    'CAN_MANAGE_CAREER_PATHWAY','CAN_MANAGE_ORG_CHART','CAN_MANAGE_TRAINING','CAN_ASSIGN_TRAINING',
    'CAN_MANAGE_LEARNING_MATERIAL','CAN_MANAGE_EVALUATION','CAN_MANAGE_EVALUATION_CYCLE',
    'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD','CAN_MANAGE_COMPANY_KPI','CAN_MANAGE_DEPARTMENT_KPI',
    'CAN_APPROVE_DEPARTMENT_KPI','CAN_REVIEW_INDIVIDUAL_KPI','CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE',
    'CAN_REVIEW_KPI_ASSESSMENT','CAN_MANAGE_ATTITUDE_CONFIGURATION','CAN_REVIEW_ATTITUDE_EVALUATION',
    'CAN_MANAGE_KPI_AMENDMENTS','CAN_REVISE_COMPANY_KPI','CAN_PREPARE_DEPARTMENT_KPI_REVISION',
    'CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS'));
INSERT INTO authority(name,description_key,label_key) VALUES
    ('CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS','auth.can.manage.individual.kpi.revisions.desc','auth.can.manage.individual.kpi.revisions');
-- Verified HR/MD grants are assigned explicitly through existing RBAC, never inferred here.
