-- Accept only the exact nullable DATE column that development Hibernate can create.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
            AND table_name='annual_kpi_review_period' AND column_name='attitude_self_assessment_start_date')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
            AND table_name='annual_kpi_review_period' AND column_name='attitude_self_assessment_start_date'
            AND data_type='date' AND is_nullable='YES' AND column_default IS NULL) THEN
        RAISE EXCEPTION 'Incompatible Attitude opening date column; reconcile before migration' USING ERRCODE='23514';
    END IF;
END $$;

ALTER TABLE annual_kpi_review_period ADD COLUMN IF NOT EXISTS attitude_self_assessment_start_date DATE;

-- Published periods previously became available when the Annual Review Period opened.
-- Refuse conflicting untracked schedules instead of postponing existing assessments.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM annual_kpi_review_period WHERE status<>'DRAFT'
            AND attitude_self_assessment_start_date IS NOT NULL
            AND attitude_self_assessment_start_date IS DISTINCT FROM start_date) THEN
        RAISE EXCEPTION 'Untracked published Attitude opening dates require review before migration' USING ERRCODE='23514';
    END IF;
END $$;

UPDATE annual_kpi_review_period
SET attitude_self_assessment_start_date=start_date
WHERE status IN ('UPCOMING','OPEN','CLOSED') AND attitude_self_assessment_start_date IS NULL;

ALTER TABLE annual_kpi_review_period
    ADD CONSTRAINT ck_period_attitude_opening_required
        CHECK (status='DRAFT' OR attitude_self_assessment_start_date IS NOT NULL),
    ADD CONSTRAINT ck_period_attitude_opening_order
        CHECK ((start_date IS NULL OR attitude_self_assessment_start_date>=start_date)
            AND (attitude_self_assessment_deadline IS NULL
                OR attitude_self_assessment_start_date<=attitude_self_assessment_deadline));
