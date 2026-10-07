-- System-role eligibility is independent of review permissions and Employee Level classification.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'annual_kpi_review_period'
          AND column_name = 'kpi_setup_deadline'
          AND (data_type <> 'date' OR is_nullable <> 'YES' OR column_default IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'V30 requires review of the unexpected existing KPI Setup Deadline column';
    END IF;
    IF (SELECT COUNT(*) FROM role WHERE LOWER(TRIM(name)) = 'superadmin') <> 1 THEN
        RAISE EXCEPTION 'V30 requires one explicitly identified Super Admin role';
    END IF;
    IF EXISTS (
        SELECT 1 FROM review_period_participant p
        LEFT JOIN role snapshot_role ON snapshot_role.id = p.role_id
        JOIN staff s ON s.id = p.staff_id
        LEFT JOIN role staff_role ON staff_role.id = s.role_id
        WHERE LOWER(TRIM(snapshot_role.name)) = 'superadmin'
           OR LOWER(TRIM(staff_role.name)) = 'superadmin'
    ) THEN
        RAISE EXCEPTION 'V30 requires manual review of existing Super Admin participant records; no historical participants will be deleted';
    END IF;
END $$;

ALTER TABLE role ADD COLUMN performance_review_eligible BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE role SET performance_review_eligible = FALSE WHERE LOWER(TRIM(name)) = 'superadmin';

-- Remove only schedules made unused by excluded system roles.
WITH removed AS (
    DELETE FROM review_period_role_configuration c
    USING role r WHERE c.role_id = r.id AND NOT r.performance_review_eligible
    RETURNING c.review_period_id, c.review_frequency
)
DELETE FROM review_checkpoint checkpoint
WHERE EXISTS (
    SELECT 1 FROM removed WHERE removed.review_period_id = checkpoint.review_period_id
      AND removed.review_frequency = checkpoint.review_frequency
) AND NOT EXISTS (
    SELECT 1 FROM review_period_role_configuration configuration JOIN role r ON r.id = configuration.role_id
    WHERE configuration.review_period_id = checkpoint.review_period_id
      AND configuration.review_frequency = checkpoint.review_frequency AND r.performance_review_eligible
);

-- Hibernate update may already have created this plain DATE column in development.
ALTER TABLE annual_kpi_review_period ADD COLUMN IF NOT EXISTS kpi_setup_deadline DATE;
UPDATE annual_kpi_review_period SET kpi_setup_deadline = COALESCE(kpi_setup_deadline, GREATEST(
    company_kpi_creation_deadline, department_kpi_creation_deadline,
    individual_kpi_submission_deadline, individual_kpi_approval_deadline
));

-- Retain the four old columns as historical evidence, not active configuration.
ALTER TABLE annual_kpi_review_period ADD CONSTRAINT chk_annual_kpi_setup_deadline
    CHECK (kpi_setup_deadline <= start_date);
ALTER TABLE annual_kpi_review_period ADD CONSTRAINT chk_annual_kpi_published_setup_deadline
    CHECK (status = 'DRAFT' OR kpi_setup_deadline IS NOT NULL);
