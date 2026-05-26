CREATE TABLE evaluation_cycle (
                                  id BIGSERIAL PRIMARY KEY,

                                  start_date DATE NOT NULL,
                                  end_date DATE NOT NULL,

                                  status VARCHAR(20) NOT NULL,

                                  all_departments BOOLEAN DEFAULT TRUE,

                                  created_at TIMESTAMP,
                                  created_by UUID,

                                  updated_at TIMESTAMP,
                                  updated_by UUID,

                                  opened_at TIMESTAMP,
                                  closed_at TIMESTAMP,

                                  CONSTRAINT chk_evaluation_cycle_dates
                                      CHECK (start_date <= end_date),

                                  CONSTRAINT chk_evaluation_cycle_status
                                      CHECK (status IN ('UPCOMING', 'OPEN', 'CLOSED'))
);

CREATE INDEX idx_evaluation_cycle_status
    ON evaluation_cycle(status);

CREATE INDEX idx_evaluation_cycle_dates
    ON evaluation_cycle(start_date, end_date);

INSERT INTO authority (name, description_key, label_key)
VALUES ('CAN_MANAGE_EVALUATION_CYCLE', 'auth.can.manage.evaluation.cycle.desc', 'auth.can.manage.evaluation.cycle');

ALTER TABLE evaluation
ALTER COLUMN overall_score TYPE DOUBLE PRECISION
USING overall_score::DOUBLE PRECISION;
