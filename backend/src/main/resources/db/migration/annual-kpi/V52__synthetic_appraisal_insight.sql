-- Separate synthetic-only preview history. No historical appraisal/assessment rows or permissions change.
CREATE TABLE appraisal_insight_generation (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES staff(id),
    request_key UUID NOT NULL,
    scenario VARCHAR(40) NOT NULL CHECK (scenario IN ('CONSISTENT_RESULTS','MIXED_RESULTS','LIMITED_EVIDENCE',
        'NON_COMPARABLE_HISTORY','ZERO_AND_TRAINING','INSTRUCTION_LIKE_TEXT')),
    data_mode VARCHAR(24) NOT NULL CHECK (data_mode = 'SYNTHETIC_ONLY'),
    fixture_version VARCHAR(32) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    model VARCHAR(100) NOT NULL,
    evidence_hash VARCHAR(64) NOT NULL CHECK (evidence_hash ~ '^[0-9a-f]{64}$'),
    evidence JSONB NOT NULL CHECK (jsonb_typeof(evidence) = 'object'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED')),
    active_key VARCHAR(180),
    error_code VARCHAR(32) CHECK (error_code IN ('INTERRUPTED','BUSY','ACCESS_CHANGED','EVIDENCE_CHANGED',
        'NOT_CONFIGURED','BLOCKED_OR_EMPTY','INCOMPLETE_RESPONSE','INVALID_RESPONSE','RATE_LIMITED',
        'PROVIDER_UNAVAILABLE','GENERATION_FAILED')),
    summary JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_insight_request UNIQUE (owner_id, request_key),
    CONSTRAINT uq_insight_active UNIQUE (active_key),
    CONSTRAINT ck_insight_state CHECK (
        (status = 'QUEUED' AND active_key = owner_id::text AND active_key IS NOT NULL
            AND started_at IS NULL AND completed_at IS NULL AND summary IS NULL AND error_code IS NULL)
        OR (status = 'RUNNING' AND active_key = owner_id::text AND active_key IS NOT NULL
            AND started_at IS NOT NULL AND completed_at IS NULL AND summary IS NULL AND error_code IS NULL)
        OR (status = 'COMPLETED' AND active_key IS NULL AND started_at IS NOT NULL AND completed_at IS NOT NULL
            AND summary IS NOT NULL AND jsonb_typeof(summary) = 'object' AND error_code IS NULL)
        OR (status = 'FAILED' AND active_key IS NULL AND completed_at IS NOT NULL AND summary IS NULL AND error_code IS NOT NULL)
    ),
    CONSTRAINT ck_insight_dates CHECK ((started_at IS NULL OR started_at >= created_at)
        AND (completed_at IS NULL OR completed_at >= COALESCE(started_at, created_at)))
);

CREATE FUNCTION guard_appraisal_insight_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'AI preview history cannot be deleted' USING ERRCODE = '23514';
    END IF;
    IF OLD.status IN ('COMPLETED','FAILED') OR
        ROW(NEW.id,NEW.owner_id,NEW.request_key,NEW.scenario,NEW.data_mode,NEW.fixture_version,
            NEW.template_version,NEW.model,NEW.evidence_hash,NEW.evidence,NEW.created_at)
        IS DISTINCT FROM
        ROW(OLD.id,OLD.owner_id,OLD.request_key,OLD.scenario,OLD.data_mode,OLD.fixture_version,
            OLD.template_version,OLD.model,OLD.evidence_hash,OLD.evidence,OLD.created_at) THEN
        RAISE EXCEPTION 'AI preview evidence and completed history are immutable' USING ERRCODE = '23514';
    END IF;
    IF NOT ((OLD.status = 'QUEUED' AND NEW.status IN ('RUNNING','FAILED'))
        OR (OLD.status = 'RUNNING' AND NEW.status IN ('COMPLETED','FAILED'))) THEN
        RAISE EXCEPTION 'Invalid AI preview transition' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_appraisal_insight_history BEFORE UPDATE OR DELETE ON appraisal_insight_generation
    FOR EACH ROW EXECUTE FUNCTION guard_appraisal_insight_history();
