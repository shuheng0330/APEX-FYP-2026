-- A returned record requires a saved content change before another submission.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
        AND table_name IN ('kpi_plan','appraisal_record') AND column_name='revision_required'
        AND (udt_name <> 'bool' OR (column_default IS NOT NULL AND column_default <> 'false'))) THEN
        RAISE EXCEPTION 'Incompatible untracked revision-required column';
    END IF;
END $$;
ALTER TABLE kpi_plan ADD COLUMN IF NOT EXISTS revision_required BOOLEAN DEFAULT FALSE;
ALTER TABLE appraisal_record ADD COLUMN IF NOT EXISTS revision_required BOOLEAN DEFAULT FALSE;
UPDATE kpi_plan SET revision_required=FALSE WHERE revision_required IS NULL;
UPDATE appraisal_record SET revision_required=FALSE WHERE revision_required IS NULL;
UPDATE kpi_plan SET revision_required=TRUE WHERE status='RETURNED';
-- Legacy Save Draft changes RETURNED to DRAFT but retains the HR return reason.
-- No historical evidence proves that a content change occurred, so require a new change.
UPDATE appraisal_record SET revision_required=TRUE WHERE status='RETURNED'
    OR (status='DRAFT' AND nullif(trim(hr_return_reason),'') IS NOT NULL);
ALTER TABLE kpi_plan ALTER COLUMN revision_required SET DEFAULT FALSE;
ALTER TABLE kpi_plan ALTER COLUMN revision_required SET NOT NULL;
ALTER TABLE appraisal_record ALTER COLUMN revision_required SET DEFAULT FALSE;
ALTER TABLE appraisal_record ALTER COLUMN revision_required SET NOT NULL;
ALTER TABLE kpi_plan ADD CONSTRAINT ck_kpi_revision_required CHECK
    (NOT revision_required OR (level IN ('DEPARTMENT','INDIVIDUAL') AND status='RETURNED'));
ALTER TABLE appraisal_record ADD CONSTRAINT ck_appraisal_revision_required CHECK
    (NOT revision_required OR status IN ('RETURNED','DRAFT'));
