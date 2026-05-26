ALTER TABLE proposal_participant RENAME COLUMN user_uuid TO staff_id;

-- 1. Drop dependent constraints first
ALTER TABLE competency_comp_tag_proposal
DROP COLUMN comp_tag_proposal_id,
DROP COLUMN is_comp_tag_proposed,
DROP COLUMN proposed_by,
DROP CONSTRAINT IF EXISTS competency_comp_tag_proposal_proposal_id_fkey;

ALTER TABLE role_competency_proposal_item
DROP COLUMN proposed_by,
DROP CONSTRAINT IF EXISTS role_competency_proposal_item_competency_proposal_id_fkey;

-- 2. Drop old PK in competency_proposal
ALTER TABLE competency_proposal
DROP CONSTRAINT IF EXISTS competency_proposal_pkey;

-- 3. Add new column staff_id (nullable first)
ALTER TABLE competency_proposal
    ADD COLUMN staff_id UUID;

-- 4. Backfill staff_id here if necessary
-- UPDATE competency_proposal SET staff_id = ...;

-- 5. Enforce NOT NULL once populated
ALTER TABLE competency_proposal
    ALTER COLUMN staff_id SET NOT NULL;

-- 6. Add new PK
ALTER TABLE competency_proposal
    ADD CONSTRAINT competency_proposal_pkey PRIMARY KEY (proposal_id, staff_id);

-- 7. Re-add FKs
ALTER TABLE competency_proposal
    ADD CONSTRAINT competency_proposal_staff_id_fkey
        FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE;

ALTER TABLE competency_comp_tag_proposal
    ADD COLUMN staff_id UUID not null,
    ADD CONSTRAINT competency_comp_tag_proposal_proposal_id_fkey
        FOREIGN KEY (proposal_id, staff_id)
            REFERENCES competency_proposal(proposal_id, staff_id) ON DELETE CASCADE;

ALTER TABLE role_competency_proposal_item
    ADD COLUMN competency_proposal_staff_id UUID,
    ADD CONSTRAINT role_competency_proposal_item_competency_proposal_id_fkey
        FOREIGN KEY (competency_proposal_id, competency_proposal_staff_id)
        REFERENCES competency_proposal(proposal_id, staff_id) ON DELETE SET NULL;

ALTER TABLE competency_proposal
DROP COLUMN competency_name;

DROP TABLE COMP_TAG_PROPOSAL;