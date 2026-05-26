-- ALTER CAREER_PATHWAY TABLE: Drop the old "track" column
ALTER TABLE career_pathway
DROP
COLUMN track;

-- CREATE NEW TABLE: TRACK
CREATE TABLE track
(
    id         BIGSERIAL PRIMARY KEY,
    track      VARCHAR(100) NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by UUID,
    created_at TIMESTAMPTZ           DEFAULT NOW(),
    updated_by UUID,
    updated_at TIMESTAMPTZ           DEFAULT NOW(),

    CONSTRAINT fk_track_created_by FOREIGN KEY (created_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_track_updated_by FOREIGN KEY (updated_by)
        REFERENCES staff (id) ON DELETE SET NULL
);

-- CREATE NEW TABLE: CAREER_PATHWAY_TRACK
CREATE TABLE career_pathway_track
(
    career_pathway_id BIGINT NOT NULL,
    track_id          BIGINT NOT NULL,
    created_by        UUID,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_by        UUID,
    updated_at        TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT fk_cpt_career_pathway FOREIGN KEY (career_pathway_id) REFERENCES career_pathway (id) ON DELETE CASCADE,

    CONSTRAINT fk_cpt_track FOREIGN KEY (track_id) REFERENCES track (id) ON DELETE CASCADE,

    CONSTRAINT fk_cpt_created_by FOREIGN KEY (created_by) REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_cpt_updated_by FOREIGN KEY (updated_by) REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT career_pathway_track_pkey PRIMARY KEY (career_pathway_id, track_id)
);

-- CLEAN DATA IN CAREER_PATHWAY_ROLE TABLE
DELETE
FROM career_pathway_role;
DELETE
FROM career_pathway;

-- Drop old foreign key constraints (referencing old column names)
ALTER TABLE career_pathway_role DROP CONSTRAINT career_pathway_role_role_id_fkey;
ALTER TABLE career_pathway_role DROP CONSTRAINT career_pathway_role_from_role_id_fkey;
ALTER TABLE career_pathway_role DROP CONSTRAINT career_pathway_role_pathway_id_fkey;

-- ALTER CAREER_PATHWAY_ROLE: Rename columns
ALTER TABLE career_pathway_role
    RENAME COLUMN role_id TO child_id;

ALTER TABLE career_pathway_role
    RENAME COLUMN from_role_id TO parent_id;

ALTER TABLE career_pathway_role
    RENAME COLUMN pathway_id TO career_pathway_id;

-- Recreate foreign keys for the renamed columns
ALTER TABLE career_pathway_role
    ADD CONSTRAINT career_pathway_role_child_id_fkey
        FOREIGN KEY (child_id) REFERENCES role (id) ON DELETE RESTRICT;

ALTER TABLE career_pathway_role
    ADD CONSTRAINT career_pathway_role_parent_id_fkey
        FOREIGN KEY (parent_id) REFERENCES role (id) ON DELETE RESTRICT;

ALTER TABLE career_pathway_role
    ADD CONSTRAINT career_pathway_role_career_pathway_id_fkey
        FOREIGN KEY (career_pathway_id) REFERENCES career_pathway (id) ON DELETE RESTRICT;

-- add composite primary key (pathway_id + parent_id + child_id)
ALTER TABLE career_pathway_role
    ADD CONSTRAINT career_pathway_role_pkey PRIMARY KEY (career_pathway_id, parent_id, child_id);

ALTER TABLE career_pathway
    ADD COLUMN IF NOT EXISTS root_role_id BIGINT;

ALTER TABLE career_pathway
    ADD CONSTRAINT career_pathway_root_role_id_fkey
        FOREIGN KEY (root_role_id) REFERENCES role (id) ON DELETE RESTRICT;