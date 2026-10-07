-- Separate annual KPI management from the retained legacy Evaluation Cycle permission.
-- Refuse an ambiguous/missing Super Admin instead of granting access to unrelated roles.
DO $$
BEGIN
    IF (SELECT count(*) FROM role WHERE lower(trim(name)) = 'superadmin' AND NOT is_deleted) <> 1 THEN
        RAISE EXCEPTION 'Expected one active superadmin role; confirm RBAC data before applying V28';
    END IF;
    IF (SELECT count(*) FROM authority WHERE name = 'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD') > 1 THEN
        RAISE EXCEPTION 'Duplicate annual KPI review period authorities; reconcile RBAC data before applying V28';
    END IF;
END $$;

-- The inherited Hibernate enum CHECK would otherwise reject the new authority.
ALTER TABLE authority DROP CONSTRAINT IF EXISTS authority_name_check;
ALTER TABLE authority ADD CONSTRAINT authority_name_check CHECK (name IN (
    'ROLE_USER', 'CAN_VIEW_ACCESS_CONTROL', 'CAN_MANAGE_ACCESS_CONTROL',
    'CAN_VIEW_STAFF', 'CAN_MANAGE_STAFF', 'CAN_VIEW_INVISIBLE_ROLE',
    'CAN_MANAGE_ROLE', 'CAN_MANAGE_COMPETENCY', 'CAN_PROPOSE_ROLE_COMPETENCIES',
    'CAN_MANAGE_CAREER_PATHWAY', 'CAN_MANAGE_ORG_CHART', 'CAN_MANAGE_TRAINING',
    'CAN_ASSIGN_TRAINING', 'CAN_MANAGE_LEARNING_MATERIAL', 'CAN_MANAGE_EVALUATION',
    'CAN_MANAGE_EVALUATION_CYCLE', 'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD'
));

INSERT INTO authority (name, description_key, label_key)
SELECT 'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD',
       'auth.can.manage.annual.kpi.review.period.desc',
       'auth.can.manage.annual.kpi.review.period'
WHERE NOT EXISTS (SELECT 1 FROM authority WHERE name = 'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD');

INSERT INTO role_authority (role_id, authority_id, created_at, updated_at)
SELECT r.id, a.id, now(), now()
FROM role r CROSS JOIN authority a
WHERE lower(trim(r.name)) = 'superadmin' AND NOT r.is_deleted
  AND a.name = 'CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD'
ON CONFLICT (role_id, authority_id) DO NOTHING;
