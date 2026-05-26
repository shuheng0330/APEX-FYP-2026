-- Drop existing table if it exists
DROP TABLE IF EXISTS role_competency_item;
DROP TABLE IF EXISTS role_competency_proposal_item;

-- Recreate tables
-- For existing competencies
CREATE TABLE role_competency_item
(
    proposal_id   BIGINT NOT NULL,
    staff_id      UUID   NOT NULL,
    role_id       BIGINT NOT NULL,
    competency_id BIGINT NOT NULL,
    weightage     BIGINT DEFAULT 0,
    CONSTRAINT pk_role_competency_item PRIMARY KEY (proposal_id, staff_id, role_id, competency_id),
    CONSTRAINT fk_rci_proposal FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_rci_staff FOREIGN KEY (staff_id) REFERENCES staff (id) ON DELETE CASCADE,
    CONSTRAINT fk_rci_role FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    CONSTRAINT fk_rci_competency FOREIGN KEY (competency_id) REFERENCES competency (id) ON DELETE CASCADE
);

-- For proposed competencies
CREATE TABLE role_competency_proposal_item
(
    proposal_id                  BIGINT NOT NULL,
    staff_id                     UUID   NOT NULL,
    role_id                      BIGINT NOT NULL,
    competency_proposal_id       BIGINT NOT NULL,
    competency_proposal_staff_id UUID   NOT NULL,
    weightage                    BIGINT DEFAULT 0,
    CONSTRAINT pk_role_competency_proposal_item PRIMARY KEY (proposal_id, staff_id, role_id, competency_proposal_id,
                                                             competency_proposal_staff_id),
    CONSTRAINT fk_rcpi_proposal FOREIGN KEY (proposal_id) REFERENCES proposal (id) ON DELETE CASCADE,
    CONSTRAINT fk_rcpi_staff FOREIGN KEY (staff_id) REFERENCES staff (id) ON DELETE CASCADE,
    CONSTRAINT fk_rcpi_role FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    CONSTRAINT fk_rcpi_competency_proposal FOREIGN KEY (competency_proposal_id, competency_proposal_staff_id)
        REFERENCES competency_proposal (proposal_id, staff_id) ON DELETE CASCADE
);

-- Drop existing table if it exists
DROP TABLE IF EXISTS role_job_scope_proposal;

-- Recreate table
CREATE TABLE role_job_scope_proposal
(
    proposal_id  BIGINT NOT NULL,
    staff_id     UUID   NOT NULL,
    role_id      BIGINT NOT NULL,
    job_scope_id BIGINT NOT NULL,
    created_by   UUID,
    created_at   TIMESTAMPTZ DEFAULT NOW(),
    updated_by   UUID,
    updated_at   TIMESTAMPTZ DEFAULT NOW(),

    -- Primary key
    CONSTRAINT pk_role_job_scope_proposal PRIMARY KEY (proposal_id, staff_id, role_id, job_scope_id),

    -- Foreign key constraints
    CONSTRAINT fk_rcp_proposal FOREIGN KEY (proposal_id)
        REFERENCES proposal (id) ON DELETE CASCADE,

    CONSTRAINT fk_rcp_staff FOREIGN KEY (staff_id)
        REFERENCES staff (id) ON DELETE CASCADE,

    CONSTRAINT fk_rcp_created_by FOREIGN KEY (created_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_rcp_updated_by FOREIGN KEY (updated_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_rcp_role FOREIGN KEY (role_id)
        REFERENCES role (id) ON DELETE CASCADE,

    CONSTRAINT fk_rcp_job_scope FOREIGN KEY (job_scope_id)
        REFERENCES job_scope (id) ON DELETE CASCADE
);

-- Drop existing table if it exists
DROP TABLE IF EXISTS role_competency_proposal;

-- Recreate table
CREATE TABLE role_competency_proposal
(
    proposal_id BIGINT NOT NULL,
    staff_id    UUID   NOT NULL,
    role_id     BIGINT NOT NULL,
    description VARCHAR(1000),
    created_by  UUID,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_by  UUID,
    updated_at  TIMESTAMPTZ DEFAULT NOW(),

    -- Primary key
    CONSTRAINT pk_role_competency_proposal PRIMARY KEY (proposal_id, staff_id, role_id),

    -- Foreign key constraints
    CONSTRAINT fk_rcp_proposal FOREIGN KEY (proposal_id)
        REFERENCES proposal (id) ON DELETE CASCADE,

    CONSTRAINT fk_rcp_staff FOREIGN KEY (staff_id)
        REFERENCES staff (id) ON DELETE CASCADE,

    CONSTRAINT fk_rcp_created_by FOREIGN KEY (created_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_rcp_updated_by FOREIGN KEY (updated_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_rcp_role FOREIGN KEY (role_id)
        REFERENCES role (id) ON DELETE CASCADE
);



