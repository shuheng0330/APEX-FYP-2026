-- Existing requests remain unchanged; the API requires a reason for new requests.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema=current_schema() AND table_name='individual_kpi_assistance_authorization'
          AND column_name='request_reason'
          AND (data_type <> 'text' OR is_nullable <> 'YES' OR column_default IS NOT NULL)) THEN
        RAISE EXCEPTION 'Incompatible existing assistance request_reason column; reconcile before V38';
    END IF;
END $$;
ALTER TABLE individual_kpi_assistance_authorization
    ADD COLUMN IF NOT EXISTS request_reason TEXT,
    ADD CONSTRAINT ck_assistance_request_reason CHECK (
        request_reason IS NULL OR
        (request_reason !~ '^[[:space:]]*$' AND length(request_reason) BETWEEN 1 AND 10000));
