-- NULL deliberately leaves inherited published periods without a fabricated roster.
ALTER TABLE annual_kpi_review_period ADD COLUMN participants_snapshotted_at TIMESTAMPTZ;
ALTER TABLE kpi_plan ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE kpi_plan ADD COLUMN published_by UUID REFERENCES staff(id);
ALTER TABLE kpi_plan ADD COLUMN published_late BOOLEAN;
ALTER TABLE kpi_plan ADD CONSTRAINT ck_company_publication_metadata CHECK
    (status <> 'PUBLISHED' OR (published_at IS NOT NULL AND published_by IS NOT NULL AND published_late IS NOT NULL));
