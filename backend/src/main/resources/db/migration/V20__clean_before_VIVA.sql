UPDATE staff
set role_id=null,
    career_pathway_id=null;

ALTER TABLE staff DROP CONSTRAINT staff_role_id_fkey1;
ALTER TABLE staff DROP CONSTRAINT fk78o57wbqp5qn4728ft5qut9wn;

TRUNCATE TABLE
    career_pathway,
    career_pathway_role,
    career_pathway_track,
    track,
    competency,
    comp_tag,
    competency_comp_tag,
    role,
    role_authority,
    role_competencies,
    role_competency_item,
    role_job_scope,
    job_scope,
    org_chart,
    parent_child_node,
    proposal,
    proposal_participant,
    competency_proposal,
    competency_comp_tag_proposal,
    role_competency_proposal,
    role_competency_proposal_item,
    role_job_scope_proposal,
    learning_document,
    learning_material,
    learning_material_competency,
    learning_material_org_chart,
    training_program,
    training_program_competency,
    training_program_org_chart,
    training_invitation,
    training_attendance,
    training_registration,
    training_target_role,
    evaluation,
    evaluation_cycle,
    evaluation_ratings,
    staff_learning_document_progress,
    staff_learning_material,
    staff_self_declared_skill,
    staff_temp,
    staff_cert
RESTART IDENTITY CASCADE;

ALTER TABLE staff
    ADD CONSTRAINT staff_role_id_fkey1
        FOREIGN KEY (role_id) REFERENCES role (id);

ALTER TABLE staff
    ADD CONSTRAINT staff_career_pathway_id_fkey2
        FOREIGN KEY (career_pathway_id) REFERENCES career_pathway (id);

delete
from staff
where is_deleted = true;

INSERT INTO org_chart (type, name, is_deleted, is_root, created_by, created_at, updated_by, updated_at)
VALUES ('P', 'superadmin', false, false, NULL, now(), NULL, now());

INSERT INTO role (org_chart_id, name, is_visible, is_deleted, created_by, created_at, updated_by, updated_at)
VALUES ((SELECT id FROM org_chart WHERE name = 'superadmin'),
        'superadmin',
        false,
        false,
        NULL,
        now(),
        NULL,
        now());

INSERT INTO role_authority (role_id, authority_id, created_by, created_at, updated_by, updated_at)
SELECT (SELECT id FROM role WHERE name = 'superadmin') AS role_id,
       id                                              AS authority_id,
       NULL                                            AS created_by,
       now()                                           AS created_at,
       NULL                                            AS updated_by,
       now()                                           AS updated_at
FROM authority;

UPDATE staff set role_id =1;