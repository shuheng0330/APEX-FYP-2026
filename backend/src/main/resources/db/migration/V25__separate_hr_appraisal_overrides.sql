ALTER TABLE appraisal_record
    ADD COLUMN IF NOT EXISTS promotion_hr_override_category VARCHAR(30),
    ADD COLUMN IF NOT EXISTS promotion_hr_override_reason TEXT,
    ADD COLUMN IF NOT EXISTS salary_hr_override_category VARCHAR(30),
    ADD COLUMN IF NOT EXISTS salary_hr_override_reason TEXT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'appraisal_record'
          AND column_name = 'hr_override_category'
    ) THEN
        UPDATE appraisal_record
        SET promotion_hr_override_category = COALESCE(promotion_hr_override_category, hr_override_category),
            promotion_hr_override_reason = COALESCE(promotion_hr_override_reason, hr_override_reason)
        WHERE decision_type IN ('PROMOTION', 'BOTH')
          AND hr_override_category IS NOT NULL;

        UPDATE appraisal_record
        SET salary_hr_override_category = COALESCE(salary_hr_override_category, hr_override_category),
            salary_hr_override_reason = COALESCE(salary_hr_override_reason, hr_override_reason)
        WHERE decision_type IN ('SALARY_INCREMENT', 'BOTH')
          AND hr_override_category IS NOT NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_appraisal_record_promotion_hr_override_category'
    ) THEN
        ALTER TABLE appraisal_record
            ADD CONSTRAINT chk_appraisal_record_promotion_hr_override_category
                CHECK (promotion_hr_override_category IS NULL OR promotion_hr_override_category IN ('READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_appraisal_record_salary_hr_override_category'
    ) THEN
        ALTER TABLE appraisal_record
            ADD CONSTRAINT chk_appraisal_record_salary_hr_override_category
                CHECK (salary_hr_override_category IS NULL OR salary_hr_override_category IN ('READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT'));
    END IF;
END $$;

ALTER TABLE appraisal_record
    DROP CONSTRAINT IF EXISTS chk_appraisal_record_hr_override_category,
    DROP COLUMN IF EXISTS hr_override_category,
    DROP COLUMN IF EXISTS hr_override_reason;
