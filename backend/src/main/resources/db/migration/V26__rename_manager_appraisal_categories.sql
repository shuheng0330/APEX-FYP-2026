DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'promotion_final_category'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'promotion_manager_category'
    ) THEN
        ALTER TABLE appraisal_record RENAME COLUMN promotion_final_category TO promotion_manager_category;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'promotion_final_category'
    ) THEN
        UPDATE appraisal_record
        SET promotion_manager_category = COALESCE(promotion_manager_category, promotion_final_category);
        ALTER TABLE appraisal_record DROP COLUMN promotion_final_category;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'salary_final_category'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'salary_manager_category'
    ) THEN
        ALTER TABLE appraisal_record RENAME COLUMN salary_final_category TO salary_manager_category;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'salary_final_category'
    ) THEN
        UPDATE appraisal_record
        SET salary_manager_category = COALESCE(salary_manager_category, salary_final_category);
        ALTER TABLE appraisal_record DROP COLUMN salary_final_category;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'promotion_override_reason'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'promotion_manager_override_reason'
    ) THEN
        ALTER TABLE appraisal_record RENAME COLUMN promotion_override_reason TO promotion_manager_override_reason;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'promotion_override_reason'
    ) THEN
        UPDATE appraisal_record
        SET promotion_manager_override_reason = COALESCE(promotion_manager_override_reason, promotion_override_reason);
        ALTER TABLE appraisal_record DROP COLUMN promotion_override_reason;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'salary_override_reason'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'salary_manager_override_reason'
    ) THEN
        ALTER TABLE appraisal_record RENAME COLUMN salary_override_reason TO salary_manager_override_reason;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appraisal_record' AND column_name = 'salary_override_reason'
    ) THEN
        UPDATE appraisal_record
        SET salary_manager_override_reason = COALESCE(salary_manager_override_reason, salary_override_reason);
        ALTER TABLE appraisal_record DROP COLUMN salary_override_reason;
    END IF;
END $$;

ALTER TABLE appraisal_record
    DROP CONSTRAINT IF EXISTS chk_appraisal_record_promotion_final_category,
    DROP CONSTRAINT IF EXISTS chk_appraisal_record_salary_final_category;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_appraisal_record_promotion_manager_category'
    ) THEN
        ALTER TABLE appraisal_record
            ADD CONSTRAINT chk_appraisal_record_promotion_manager_category
                CHECK (promotion_manager_category IS NULL OR promotion_manager_category IN ('READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_appraisal_record_salary_manager_category'
    ) THEN
        ALTER TABLE appraisal_record
            ADD CONSTRAINT chk_appraisal_record_salary_manager_category
                CHECK (salary_manager_category IS NULL OR salary_manager_category IN ('READY', 'BORDERLINE', 'NEEDS_IMPROVEMENT'));
    END IF;
END $$;
