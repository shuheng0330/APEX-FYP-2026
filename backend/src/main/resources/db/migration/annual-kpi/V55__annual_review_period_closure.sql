-- Existing historical closing actors are unknown; never infer them from updated_by.
ALTER TABLE annual_kpi_review_period ADD COLUMN closed_by UUID;
ALTER TABLE annual_kpi_review_period ADD CONSTRAINT fk_review_period_closed_by
    FOREIGN KEY (closed_by) REFERENCES staff(id);
ALTER TABLE annual_kpi_review_period ADD CONSTRAINT ck_review_period_closure_actor
    CHECK (closed_by IS NULL OR (status = 'CLOSED' AND closed_at IS NOT NULL));
