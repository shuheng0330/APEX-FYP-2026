DO $$
DECLARE existing_count INTEGER; compatible_count INTEGER;
BEGIN
    SELECT count(*), count(*) FILTER (WHERE is_nullable='YES' AND column_default IS NULL AND
        udt_name=CASE column_name WHEN 'latest_return_reason' THEN 'text'
            WHEN 'latest_returned_at' THEN 'timestamptz' WHEN 'latest_returned_by' THEN 'uuid' ELSE 'jsonb' END)
    INTO existing_count,compatible_count FROM information_schema.columns
    WHERE table_schema=current_schema() AND table_name='kpi_plan' AND column_name IN
        ('latest_return_reason','latest_returned_at','latest_returned_by','latest_return_items');
    IF existing_count NOT IN (0,4) OR compatible_count<>existing_count THEN
        RAISE EXCEPTION 'Incompatible or partial untracked return feedback columns; reconcile explicitly';
    END IF;
END $$;
ALTER TABLE kpi_plan
    ADD COLUMN IF NOT EXISTS latest_return_reason TEXT,
    ADD COLUMN IF NOT EXISTS latest_returned_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS latest_returned_by UUID,
    ADD COLUMN IF NOT EXISTS latest_return_items JSONB;
ALTER TABLE kpi_plan ADD CONSTRAINT fk_kpi_plan_latest_return_reviewer FOREIGN KEY(latest_returned_by) REFERENCES staff(id);

-- Earlier resubmissions cleared this information; retain only evidence still available.
UPDATE kpi_plan
SET latest_return_reason = return_reason,
    latest_returned_at = reviewed_at,
    latest_returned_by = reviewed_by
WHERE return_reason IS NOT NULL AND latest_return_reason IS NULL AND latest_returned_at IS NULL
    AND latest_returned_by IS NULL AND latest_return_items IS NULL;

ALTER TABLE kpi_plan ADD CONSTRAINT ck_kpi_plan_latest_return_items
    CHECK (latest_return_items IS NULL OR jsonb_typeof(latest_return_items) = 'array');
