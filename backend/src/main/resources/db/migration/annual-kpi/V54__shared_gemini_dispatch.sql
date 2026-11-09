-- Metadata-only coordination. Existing insight history, fixture scopes and permissions are unchanged.
CREATE TABLE gemini_quota_scope (
    project_key VARCHAR(80) NOT NULL,
    model VARCHAR(100) NOT NULL,
    config_revision BIGINT NOT NULL CHECK (config_revision > 0),
    workers INTEGER NOT NULL CHECK (workers BETWEEN 1 AND 5),
    queue_capacity INTEGER NOT NULL CHECK (queue_capacity BETWEEN 1 AND 128),
    requests_per_minute INTEGER NOT NULL CHECK (requests_per_minute > 0),
    tokens_per_minute BIGINT NOT NULL CHECK (tokens_per_minute > 0),
    requests_per_day INTEGER NOT NULL CHECK (requests_per_day > 0),
    queue_wait_seconds INTEGER NOT NULL CHECK (queue_wait_seconds BETWEEN 1 AND 3600),
    retry_until TIMESTAMPTZ,
    PRIMARY KEY(project_key,model)
);

CREATE TABLE gemini_appraisal_work (
    id UUID PRIMARY KEY,
    project_key VARCHAR(80) NOT NULL,
    model VARCHAR(100) NOT NULL,
    instance_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('EMPLOYEE','SYNTHETIC')),
    token_bound BIGINT NOT NULL CHECK (token_bound > 0),
    phase VARCHAR(16) NOT NULL CHECK (phase IN ('QUEUED','RESERVED','DISPATCHED','FINISHED','CANCELLED')),
    enqueued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    permit_id UUID,
    FOREIGN KEY(project_key,model) REFERENCES gemini_quota_scope(project_key,model),
    CHECK ((phase IN ('RESERVED','DISPATCHED') AND lease_until IS NOT NULL AND permit_id IS NOT NULL)
        OR (phase NOT IN ('RESERVED','DISPATCHED') AND lease_until IS NULL)),
    CHECK (expires_at >= enqueued_at)
);
CREATE INDEX idx_gemini_work_capacity ON gemini_appraisal_work(project_key,model,phase,lease_until);
CREATE INDEX idx_gemini_work_queue ON gemini_appraisal_work(instance_id,phase,enqueued_at,id);

CREATE TABLE gemini_request_permit (
    id UUID PRIMARY KEY,
    project_key VARCHAR(80) NOT NULL,
    model VARCHAR(100) NOT NULL,
    work_id UUID REFERENCES gemini_appraisal_work(id),
    phase VARCHAR(16) NOT NULL CHECK (phase IN ('RESERVED','DISPATCHED','FINISHED','RELEASED','UNCERTAIN')),
    reserved_tokens BIGINT NOT NULL CHECK (reserved_tokens > 0),
    charged_tokens BIGINT NOT NULL CHECK (charged_tokens >= 0),
    reserved_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ NOT NULL,
    dispatched_at TIMESTAMPTZ,
    FOREIGN KEY(project_key,model) REFERENCES gemini_quota_scope(project_key,model),
    CHECK ((phase IN ('DISPATCHED','FINISHED','UNCERTAIN') AND dispatched_at IS NOT NULL)
        OR (phase IN ('RESERVED','RELEASED') AND dispatched_at IS NULL))
);
CREATE INDEX idx_gemini_permit_budget ON gemini_request_permit(project_key,model,dispatched_at);
CREATE INDEX idx_gemini_permit_reservation ON gemini_request_permit(project_key,model,phase,lease_until);
