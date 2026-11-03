-- Retain rejected requests as history while permitting a fresh eligible request.
ALTER TABLE individual_kpi_assistance_authorization
    ADD COLUMN rejected_at TIMESTAMPTZ,
    ADD COLUMN rejected_by UUID REFERENCES staff(id),
    ADD COLUMN rejection_reason TEXT;
ALTER TABLE individual_kpi_assistance_authorization DROP CONSTRAINT uq_assistance_case;
CREATE UNIQUE INDEX uq_assistance_active_case
    ON individual_kpi_assistance_authorization(owner_participant_id,superior_id)
    WHERE status <> 'REJECTED';
ALTER TABLE individual_kpi_assistance_authorization DROP CONSTRAINT ck_assistance_state;
ALTER TABLE individual_kpi_assistance_authorization ADD CONSTRAINT ck_assistance_state CHECK (
    (status='REQUESTED' AND authorized_at IS NULL AND authorized_by IS NULL AND consumed_at IS NULL
        AND rejected_at IS NULL AND rejected_by IS NULL AND rejection_reason IS NULL) OR
    (status='AUTHORIZED' AND authorized_at IS NOT NULL AND authorized_by IS NOT NULL AND consumed_at IS NULL
        AND rejected_at IS NULL AND rejected_by IS NULL AND rejection_reason IS NULL) OR
    (status='CONSUMED' AND authorized_at IS NOT NULL AND authorized_by IS NOT NULL AND consumed_at IS NOT NULL
        AND rejected_at IS NULL AND rejected_by IS NULL AND rejection_reason IS NULL) OR
    (status='REJECTED' AND authorized_at IS NULL AND authorized_by IS NULL AND consumed_at IS NULL
        AND rejected_at IS NOT NULL AND rejected_by IS NOT NULL AND rejection_reason IS NOT NULL
        AND length(btrim(rejection_reason)) BETWEEN 1 AND 10000));
ALTER TABLE individual_kpi_assistance_authorization DROP CONSTRAINT ck_assistance_dates;
ALTER TABLE individual_kpi_assistance_authorization ADD CONSTRAINT ck_assistance_dates
    CHECK (authorized_at >= requested_at AND consumed_at >= authorized_at AND rejected_at >= requested_at);
