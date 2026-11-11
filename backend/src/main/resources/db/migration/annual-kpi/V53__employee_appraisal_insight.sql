-- Stage 2 has its own registry and history. V52 synthetic scenario previews remain unchanged.
-- No fixture scopes are seeded: isolated UAT provenance must be verified before registration.
CREATE TABLE appraisal_employee_insight_scope (
    participant_id BIGINT PRIMARY KEY,
    employee_id UUID NOT NULL,
    review_period_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT false,
    expected_database_marker VARCHAR(255) NOT NULL CHECK (length(btrim(expected_database_marker)) > 0),
    provenance TEXT NOT NULL CHECK (length(btrim(provenance)) > 0),
    source_roots JSONB NOT NULL,
    CONSTRAINT uq_employee_insight_scope_context UNIQUE (participant_id,employee_id,review_period_id),
    CONSTRAINT fk_employee_insight_scope_participant FOREIGN KEY (participant_id,review_period_id,employee_id)
        REFERENCES review_period_participant(id,review_period_id,staff_id),
    CONSTRAINT ck_employee_insight_roots CHECK (
        jsonb_typeof(source_roots) = 'object'
        AND source_roots ?& ARRAY['plans','attitudeConfigurationId','historicalParticipantIds']
        AND source_roots - ARRAY['plans','attitudeConfigurationId','historicalParticipantIds'] = '{}'::jsonb
        AND jsonb_typeof(source_roots->'plans') = 'object'
        AND source_roots->'plans' ?& ARRAY['COMPANY','DEPARTMENT','INDIVIDUAL']
        AND (source_roots->'plans') - ARRAY['COMPANY','DEPARTMENT','INDIVIDUAL'] = '{}'::jsonb
        AND jsonb_typeof(source_roots->'attitudeConfigurationId') = 'number'
        AND jsonb_typeof(source_roots->'historicalParticipantIds') = 'array')
);

CREATE FUNCTION guard_employee_insight_scope() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE root RECORD; historical_id JSONB; participant_department BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Employee insight registry cannot be deleted; disable the scope instead' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF ROW(NEW.participant_id,NEW.employee_id,NEW.review_period_id,NEW.expected_database_marker,NEW.provenance,NEW.source_roots)
            IS DISTINCT FROM
            ROW(OLD.participant_id,OLD.employee_id,OLD.review_period_id,OLD.expected_database_marker,OLD.provenance,OLD.source_roots) THEN
            RAISE EXCEPTION 'Employee insight trust roots and provenance are immutable' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    -- Validate JSON types before iteration/casting, including SQL NULL and JSON null.
    IF jsonb_typeof(NEW.source_roots) IS DISTINCT FROM 'object'
        OR jsonb_typeof(NEW.source_roots->'plans') IS DISTINCT FROM 'object'
        OR jsonb_typeof(NEW.source_roots->'historicalParticipantIds') IS DISTINCT FROM 'array'
        OR jsonb_typeof(NEW.source_roots->'attitudeConfigurationId') IS DISTINCT FROM 'number'
        OR (NEW.source_roots->>'attitudeConfigurationId') !~ '^[1-9][0-9]*$' THEN
        RAISE EXCEPTION 'Invalid employee insight trust roots' USING ERRCODE = '23514';
    END IF;
    SELECT department_id INTO participant_department FROM review_period_participant WHERE id = NEW.participant_id;
    FOR root IN SELECT key,value FROM jsonb_each(NEW.source_roots->'plans') LOOP
        IF root.key NOT IN ('COMPANY','DEPARTMENT','INDIVIDUAL')
            OR jsonb_typeof(root.value) IS DISTINCT FROM 'number' OR root.value::text !~ '^[1-9][0-9]*$' THEN
            RAISE EXCEPTION 'Invalid employee insight plan root' USING ERRCODE = '23514';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM kpi_plan p WHERE p.id::numeric = (root.value::text)::numeric
            AND p.review_period_id = NEW.review_period_id AND p.level = root.key
            AND (p.level = 'COMPANY'
                OR p.level = 'DEPARTMENT' AND p.department_id = participant_department
                OR p.level = 'INDIVIDUAL' AND p.owner_participant_id = NEW.participant_id)) THEN
            RAISE EXCEPTION 'Employee insight plan root does not match its context' USING ERRCODE = '23514';
        END IF;
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM annual_kpi_review_period p
        WHERE p.id = NEW.review_period_id AND p.attitude_configuration_id::numeric =
            (NEW.source_roots->>'attitudeConfigurationId')::numeric) THEN
        RAISE EXCEPTION 'Employee insight attitude root does not match its period' USING ERRCODE = '23514';
    END IF;
    IF (SELECT count(*) FROM jsonb_array_elements(NEW.source_roots->'historicalParticipantIds')) <>
        (SELECT count(DISTINCT value) FROM jsonb_array_elements(NEW.source_roots->'historicalParticipantIds')) THEN
        RAISE EXCEPTION 'Employee insight historical roots must be distinct' USING ERRCODE = '23514';
    END IF;
    FOR historical_id IN SELECT value FROM jsonb_array_elements(NEW.source_roots->'historicalParticipantIds') LOOP
        IF jsonb_typeof(historical_id) IS DISTINCT FROM 'number' OR historical_id::text !~ '^[1-9][0-9]*$' THEN
            RAISE EXCEPTION 'Invalid employee insight historical root' USING ERRCODE = '23514';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM review_period_participant h
            JOIN annual_kpi_review_period hp ON hp.id = h.review_period_id
            JOIN annual_kpi_review_period current_period ON current_period.id = NEW.review_period_id
            WHERE h.id::numeric = (historical_id::text)::numeric AND h.staff_id = NEW.employee_id
                AND hp.end_date < current_period.start_date) THEN
            RAISE EXCEPTION 'Employee insight historical root does not match its employee or prior period' USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_employee_insight_scope BEFORE INSERT OR UPDATE OR DELETE ON appraisal_employee_insight_scope
    FOR EACH ROW EXECUTE FUNCTION guard_employee_insight_scope();

CREATE TABLE appraisal_employee_insight_generation (
    id UUID PRIMARY KEY,
    participant_id BIGINT NOT NULL,
    employee_id UUID NOT NULL,
    review_period_id BIGINT NOT NULL,
    owner_id UUID NOT NULL REFERENCES staff(id),
    fingerprint VARCHAR(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    evidence JSONB NOT NULL CHECK (jsonb_typeof(evidence) = 'object'),
    source_references JSONB NOT NULL CHECK (jsonb_typeof(source_references) IN ('object','array')),
    model VARCHAR(100) NOT NULL CHECK (length(btrim(model)) > 0),
    template_version VARCHAR(32) NOT NULL CHECK (length(btrim(template_version)) > 0),
    data_mode VARCHAR(24) NOT NULL CHECK (data_mode = 'FICTIONAL_UAT_EMPLOYEE'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED')),
    active_context BIGINT,
    insight_version INTEGER CHECK (insight_version > 0),
    summary JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    quota_day DATE NOT NULL,
    retry_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    error_code VARCHAR(32),
    lock_version BIGINT NOT NULL DEFAULT 0 CHECK (lock_version >= 0),
    CONSTRAINT uq_employee_insight_generation_context UNIQUE (id,participant_id),
    CONSTRAINT uq_employee_insight_active UNIQUE (active_context),
    CONSTRAINT uq_employee_insight_version UNIQUE (participant_id,insight_version),
    CONSTRAINT fk_employee_insight_generation_scope FOREIGN KEY (participant_id,employee_id,review_period_id)
        REFERENCES appraisal_employee_insight_scope(participant_id,employee_id,review_period_id),
    CONSTRAINT ck_employee_insight_error CHECK (error_code IN (
        'INTERRUPTED','BUSY','ACCESS_CHANGED','EVIDENCE_CHANGED','NOT_CONFIGURED','BLOCKED_OR_EMPTY',
        'INCOMPLETE_RESPONSE','INVALID_RESPONSE','RATE_LIMITED','PROVIDER_UNAVAILABLE','GENERATION_FAILED',
        'QUOTA_EXHAUSTED','COOLDOWN','SCOPE_CHANGED','LEASE_EXPIRED')),
    CONSTRAINT ck_employee_insight_state CHECK (
        (status = 'QUEUED' AND active_context IS NOT NULL AND active_context = participant_id
            AND started_at IS NULL AND completed_at IS NULL AND dispatched_at IS NULL
            AND insight_version IS NULL AND summary IS NULL AND error_code IS NULL AND retry_at IS NULL)
        OR (status = 'RUNNING' AND active_context IS NOT NULL AND active_context = participant_id
            AND started_at IS NOT NULL AND completed_at IS NULL
            AND insight_version IS NULL AND summary IS NULL AND error_code IS NULL AND retry_at IS NULL)
        OR (status = 'COMPLETED' AND active_context IS NULL AND started_at IS NOT NULL
            AND completed_at IS NOT NULL AND dispatched_at IS NOT NULL AND insight_version IS NOT NULL
            AND summary IS NOT NULL AND jsonb_typeof(summary) = 'object' AND error_code IS NULL AND retry_at IS NULL)
        OR (status = 'FAILED' AND active_context IS NULL AND completed_at IS NOT NULL
            AND insight_version IS NULL AND summary IS NULL AND error_code IS NOT NULL)
    ),
    CONSTRAINT ck_employee_insight_lease CHECK (
        (status IN ('QUEUED','RUNNING') AND lease_until IS NOT NULL)
        OR (status IN ('COMPLETED','FAILED') AND lease_until IS NULL)
    ),
    CONSTRAINT ck_employee_insight_dates CHECK (
        (started_at IS NULL OR started_at >= created_at)
        AND (completed_at IS NULL OR completed_at >= COALESCE(started_at,created_at))
        AND (dispatched_at IS NULL OR started_at IS NOT NULL AND dispatched_at >= started_at)
        AND (completed_at IS NULL OR dispatched_at IS NULL OR completed_at >= dispatched_at)
        AND (retry_at IS NULL OR completed_at IS NOT NULL AND retry_at >= completed_at)
    )
);
CREATE INDEX idx_employee_insight_context_history
    ON appraisal_employee_insight_generation(participant_id,created_at DESC,id DESC);
CREATE INDEX idx_employee_insight_expired_lease
    ON appraisal_employee_insight_generation(lease_until) WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX idx_employee_insight_provider_retry
    ON appraisal_employee_insight_generation(retry_at DESC) WHERE retry_at IS NOT NULL;

CREATE FUNCTION guard_employee_insight_generation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Employee insight history cannot be deleted' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'QUEUED' THEN
            RAISE EXCEPTION 'Employee insight generations must start queued' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status IN ('COMPLETED','FAILED') OR
        ROW(NEW.id,NEW.participant_id,NEW.employee_id,NEW.review_period_id,NEW.owner_id,NEW.fingerprint,
            NEW.evidence,NEW.source_references,NEW.model,NEW.template_version,NEW.data_mode,NEW.created_at)
        IS DISTINCT FROM
        ROW(OLD.id,OLD.participant_id,OLD.employee_id,OLD.review_period_id,OLD.owner_id,OLD.fingerprint,
            OLD.evidence,OLD.source_references,OLD.model,OLD.template_version,OLD.data_mode,OLD.created_at) THEN
        RAISE EXCEPTION 'Employee insight inputs and terminal history are immutable' USING ERRCODE = '23514';
    END IF;
    IF NOT ((OLD.status = 'QUEUED' AND NEW.status IN ('RUNNING','FAILED'))
        OR (OLD.status = 'RUNNING' AND NEW.status IN ('RUNNING','COMPLETED','FAILED'))) THEN
        RAISE EXCEPTION 'Invalid employee insight transition' USING ERRCODE = '23514';
    END IF;
    IF OLD.started_at IS NOT NULL AND NEW.started_at IS DISTINCT FROM OLD.started_at
        OR OLD.dispatched_at IS NOT NULL AND ROW(NEW.dispatched_at,NEW.quota_day)
            IS DISTINCT FROM ROW(OLD.dispatched_at,OLD.quota_day) THEN
        RAISE EXCEPTION 'Employee insight dispatch accounting is immutable once recorded' USING ERRCODE = '23514';
    END IF;
    IF (OLD.status = 'QUEUED' AND NEW.status = 'RUNNING' AND NEW.started_at >= OLD.lease_until)
        OR (NEW.status = 'COMPLETED' AND NEW.completed_at >= OLD.lease_until)
        OR (OLD.dispatched_at IS NULL AND NEW.dispatched_at IS NOT NULL AND NEW.dispatched_at >= OLD.lease_until) THEN
        RAISE EXCEPTION 'Expired employee insight work cannot start, dispatch or complete' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_employee_insight_generation BEFORE INSERT OR UPDATE OR DELETE ON appraisal_employee_insight_generation
    FOR EACH ROW EXECUTE FUNCTION guard_employee_insight_generation();

CREATE TABLE appraisal_employee_insight_request (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES staff(id),
    request_key UUID NOT NULL,
    participant_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('GENERATE','REGENERATE')),
    generation_id UUID NOT NULL,
    disposition VARCHAR(24) NOT NULL CHECK (disposition IN ('REUSED','NEWLY_QUEUED','ALREADY_IN_PROGRESS')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_employee_insight_request UNIQUE (actor_id,request_key),
    CONSTRAINT fk_employee_insight_request_generation FOREIGN KEY (generation_id,participant_id)
        REFERENCES appraisal_employee_insight_generation(id,participant_id),
    CONSTRAINT ck_employee_insight_request_operation CHECK (operation <> 'REGENERATE' OR disposition <> 'REUSED')
);
CREATE INDEX idx_employee_insight_request_generation ON appraisal_employee_insight_request(generation_id);

CREATE TABLE appraisal_employee_insight_submission_link (
    submission_activity_id BIGINT PRIMARY KEY REFERENCES annual_appraisal_activity(id),
    appraisal_id BIGINT NOT NULL REFERENCES annual_appraisal(id),
    generation_id UUID NOT NULL,
    participant_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_employee_insight_submission_generation FOREIGN KEY (generation_id,participant_id)
        REFERENCES appraisal_employee_insight_generation(id,participant_id)
);
CREATE INDEX idx_employee_insight_submission_history
    ON appraisal_employee_insight_submission_link(appraisal_id,submission_activity_id DESC);

CREATE FUNCTION guard_employee_insight_append_only() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Employee insight receipts and submission links are append-only' USING ERRCODE = '23514';
END $$;
CREATE TRIGGER trg_employee_insight_request_immutable BEFORE UPDATE OR DELETE ON appraisal_employee_insight_request
    FOR EACH ROW EXECUTE FUNCTION guard_employee_insight_append_only();
CREATE TRIGGER trg_employee_insight_submission_immutable BEFORE UPDATE OR DELETE ON appraisal_employee_insight_submission_link
    FOR EACH ROW EXECUTE FUNCTION guard_employee_insight_append_only();

CREATE FUNCTION guard_employee_insight_submission_context() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM annual_appraisal_activity event
        JOIN annual_appraisal appraisal ON appraisal.id = event.appraisal_id
        JOIN appraisal_employee_insight_generation generation ON generation.id = NEW.generation_id
        WHERE event.id = NEW.submission_activity_id AND event.action IN ('SUBMITTED','RESUBMITTED')
            AND event.appraisal_id = NEW.appraisal_id AND appraisal.participant_id = NEW.participant_id
            AND generation.participant_id = appraisal.participant_id
            AND generation.employee_id = appraisal.employee_id AND generation.review_period_id = appraisal.review_period_id
            AND generation.status = 'COMPLETED') THEN
        RAISE EXCEPTION 'Employee insight link requires a matching submission event and completed context' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_employee_insight_submission_context BEFORE INSERT ON appraisal_employee_insight_submission_link
    FOR EACH ROW EXECUTE FUNCTION guard_employee_insight_submission_context();
