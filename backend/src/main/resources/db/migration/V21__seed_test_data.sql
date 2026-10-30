-- V21__seed_test_data.sql
-- Dev seed: 3 departments, 6 roles, 6 competencies, 7 staff, 1 closed cycle, full evaluation ratings
--
-- Default login password for all seed staff: Password@123
-- BCrypt hash (cost 10): $2a$10$z0nMRLOqlhZMiJ9DtwNktOIlG3J7e430wWvLOUdbl5aqQNYnVs8oi

-- â”€â”€â”€ DEPARTMENTS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- org_chart IDs: superadmin=1 (from V20), IT=2, HR=3, Sales=4

INSERT INTO org_chart (type, name, is_deleted, is_root, created_by, created_at, updated_by, updated_at)
VALUES
    ('D', 'IT Department',    false, false, NULL, now(), NULL, now()),
    ('D', 'HR Department',    false, false, NULL, now(), NULL, now()),
    ('D', 'Sales Department', false, false, NULL, now(), NULL, now());

-- Org chart hierarchy: superadmin (id=1) â†’ each department
INSERT INTO parent_child_node (parent_id, child_id, relation_type, created_by, created_at, updated_by, updated_at)
VALUES
    (1, (SELECT id FROM org_chart WHERE name = 'IT Department'),    'ORG_CHART', NULL, now(), NULL, now()),
    (1, (SELECT id FROM org_chart WHERE name = 'HR Department'),    'ORG_CHART', NULL, now(), NULL, now()),
    (1, (SELECT id FROM org_chart WHERE name = 'Sales Department'), 'ORG_CHART', NULL, now(), NULL, now());

-- â”€â”€â”€ ROLES â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

INSERT INTO role (org_chart_id, name, description, is_visible, is_deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ((SELECT id FROM org_chart WHERE name = 'IT Department'),    'IT Manager',       'Leads the IT department and technology strategy',   true, false, NULL, now(), NULL, now()),
    ((SELECT id FROM org_chart WHERE name = 'IT Department'),    'Software Engineer','Designs, develops and maintains software systems',  true, false, NULL, now(), NULL, now()),
    ((SELECT id FROM org_chart WHERE name = 'HR Department'),    'HR Manager',       'Leads human resources and people operations',       true, false, NULL, now(), NULL, now()),
    ((SELECT id FROM org_chart WHERE name = 'HR Department'),    'HR Officer',       'Handles recruitment, onboarding and HR processes',  true, false, NULL, now(), NULL, now()),
    ((SELECT id FROM org_chart WHERE name = 'Sales Department'), 'Sales Manager',    'Leads the sales team and drives revenue targets',   true, false, NULL, now(), NULL, now()),
    ((SELECT id FROM org_chart WHERE name = 'Sales Department'), 'Sales Executive',  'Manages client relationships and closes deals',     true, false, NULL, now(), NULL, now());
-- AUTHORITY SEED AND ASSIGNMENTS ------------------------------------------------------------
-- V20 keeps only the latest authority rows in some local DBs, so make sure the
-- core authorities used by login/navigation exist before assigning them to roles.
INSERT INTO authority (name, description_key, label_key)
SELECT v.name, v.description_key, v.label_key
FROM (VALUES
    ('ROLE_USER', 'auth.role.user.desc', 'auth.role.user'),
    ('CAN_MANAGE_EVALUATION_CYCLE', 'auth.can.manage.evaluation.cycle.desc', 'auth.can.manage.evaluation.cycle'),
    ('CAN_MANAGE_EVALUATION', 'auth.can.manage.evaluation.desc', 'auth.can.manage.evaluation'),
    ('CAN_MANAGE_STAFF', 'auth.can.manage.staff.desc', 'auth.can.manage.staff'),
    ('CAN_VIEW_STAFF', 'auth.can.view.staff.desc', 'auth.can.view.staff'),
    ('CAN_MANAGE_ACCESS_CONTROL', 'auth.can.manage.access.control.desc', 'auth.can.manage.access.control'),
    ('CAN_VIEW_ACCESS_CONTROL', 'auth.can.view.access.control.desc', 'auth.can.view.access.control'),
    ('CAN_MANAGE_COMPETENCY', 'auth.can.manage.competency.desc', 'auth.can.manage.competency'),
    ('CAN_MANAGE_ROLE', 'auth.can.manage.role.desc', 'auth.can.manage.role'),
    ('CAN_MANAGE_ORG_CHART', 'auth.can.manage.org.chart.desc', 'auth.can.manage.org.chart.label')
) AS v(name, description_key, label_key)
WHERE NOT EXISTS (
    SELECT 1
    FROM authority a
    WHERE a.name = v.name
);

-- Corrected role authority assignments for the V21 test roles.
INSERT INTO role_authority (role_id, authority_id, created_by, created_at, updated_by, updated_at)
SELECT r.id, a.id, NULL, now(), NULL, now()
FROM (VALUES
    ('IT Manager', 'ROLE_USER'),
    ('IT Manager', 'CAN_MANAGE_EVALUATION'),
    ('Software Engineer', 'ROLE_USER'),
    ('HR Manager', 'ROLE_USER'),
    ('HR Manager', 'CAN_MANAGE_EVALUATION'),
    ('HR Manager', 'CAN_VIEW_STAFF'),
    ('HR Officer', 'ROLE_USER'),
    ('HR Officer', 'CAN_VIEW_STAFF'),
    ('Sales Manager', 'ROLE_USER'),
    ('Sales Manager', 'CAN_MANAGE_EVALUATION'),
    ('Sales Executive', 'ROLE_USER')
) AS assignment(role_name, authority_name)
JOIN role r ON r.name = assignment.role_name
JOIN authority a ON a.name = assignment.authority_name
ON CONFLICT (role_id, authority_id) DO NOTHING;

-- Superadmin authorities for thongshuheng0330@gmail.com.
INSERT INTO role_authority (role_id, authority_id, created_by, created_at, updated_by, updated_at)
SELECT s.role_id, a.id, NULL, now(), NULL, now()
FROM staff s
JOIN authority a ON a.name IN (
    'ROLE_USER',
    'CAN_MANAGE_EVALUATION_CYCLE',
    'CAN_MANAGE_STAFF',
    'CAN_VIEW_STAFF',
    'CAN_MANAGE_ACCESS_CONTROL',
    'CAN_VIEW_ACCESS_CONTROL',
    'CAN_MANAGE_COMPETENCY',
    'CAN_MANAGE_ROLE',
    'CAN_MANAGE_ORG_CHART'
)
WHERE s.email = 'thongshuheng0330@gmail.com'
ON CONFLICT (role_id, authority_id) DO NOTHING;

-- â”€â”€â”€ COMPETENCIES â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

INSERT INTO competency (name, description, is_deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('Technical Proficiency',    'Depth of technical knowledge and ability to apply it effectively',        false, NULL, now(), NULL, now()),
    ('Communication Skills',     'Ability to convey ideas clearly and listen actively',                     false, NULL, now(), NULL, now()),
    ('Problem Solving',          'Analytical thinking and ability to resolve complex issues',               false, NULL, now(), NULL, now()),
    ('Teamwork & Collaboration', 'Effectiveness in working within cross-functional teams',                  false, NULL, now(), NULL, now()),
    ('Leadership & Initiative',  'Ability to lead, motivate others and take ownership',                    false, NULL, now(), NULL, now()),
    ('Customer Orientation',     'Focus on understanding and fulfilling customer and stakeholder needs',    false, NULL, now(), NULL, now());

-- â”€â”€â”€ ROLE COMPETENCIES (weightages per role sum to 100) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

INSERT INTO role_competencies (role_id, comp_id, weightage, created_by, created_at, updated_by, updated_at)
VALUES
    -- IT Manager: Technical(25) Communication(25) ProblemSolving(20) Leadership(30)
    ((SELECT id FROM role WHERE name = 'IT Manager'), (SELECT id FROM competency WHERE name = 'Technical Proficiency'),    25, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'IT Manager'), (SELECT id FROM competency WHERE name = 'Communication Skills'),     25, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'IT Manager'), (SELECT id FROM competency WHERE name = 'Problem Solving'),          20, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'IT Manager'), (SELECT id FROM competency WHERE name = 'Leadership & Initiative'),  30, NULL, now(), NULL, now()),

    -- Software Engineer: Technical(40) Communication(20) ProblemSolving(25) Teamwork(15)
    ((SELECT id FROM role WHERE name = 'Software Engineer'), (SELECT id FROM competency WHERE name = 'Technical Proficiency'),    40, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'Software Engineer'), (SELECT id FROM competency WHERE name = 'Communication Skills'),     20, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'Software Engineer'), (SELECT id FROM competency WHERE name = 'Problem Solving'),          25, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'Software Engineer'), (SELECT id FROM competency WHERE name = 'Teamwork & Collaboration'), 15, NULL, now(), NULL, now()),

    -- HR Manager: Communication(25) Leadership(35) Teamwork(20) ProblemSolving(20)
    ((SELECT id FROM role WHERE name = 'HR Manager'), (SELECT id FROM competency WHERE name = 'Communication Skills'),     25, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'HR Manager'), (SELECT id FROM competency WHERE name = 'Leadership & Initiative'),  35, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'HR Manager'), (SELECT id FROM competency WHERE name = 'Teamwork & Collaboration'), 20, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'HR Manager'), (SELECT id FROM competency WHERE name = 'Problem Solving'),          20, NULL, now(), NULL, now()),

    -- HR Officer: Communication(35) Teamwork(25) ProblemSolving(20) CustomerOrientation(20)
    ((SELECT id FROM role WHERE name = 'HR Officer'), (SELECT id FROM competency WHERE name = 'Communication Skills'),     35, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'HR Officer'), (SELECT id FROM competency WHERE name = 'Teamwork & Collaboration'), 25, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'HR Officer'), (SELECT id FROM competency WHERE name = 'Problem Solving'),          20, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'HR Officer'), (SELECT id FROM competency WHERE name = 'Customer Orientation'),     20, NULL, now(), NULL, now()),

    -- Sales Manager: Communication(20) CustomerOrientation(25) Leadership(35) ProblemSolving(20)
    ((SELECT id FROM role WHERE name = 'Sales Manager'), (SELECT id FROM competency WHERE name = 'Communication Skills'),     20, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'Sales Manager'), (SELECT id FROM competency WHERE name = 'Customer Orientation'),     25, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'Sales Manager'), (SELECT id FROM competency WHERE name = 'Leadership & Initiative'),  35, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'Sales Manager'), (SELECT id FROM competency WHERE name = 'Problem Solving'),          20, NULL, now(), NULL, now()),

    -- Sales Executive: Communication(30) CustomerOrientation(35) ProblemSolving(20) Teamwork(15)
    ((SELECT id FROM role WHERE name = 'Sales Executive'), (SELECT id FROM competency WHERE name = 'Communication Skills'),     30, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'Sales Executive'), (SELECT id FROM competency WHERE name = 'Customer Orientation'),     35, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'Sales Executive'), (SELECT id FROM competency WHERE name = 'Problem Solving'),          20, NULL, now(), NULL, now()),
    ((SELECT id FROM role WHERE name = 'Sales Executive'), (SELECT id FROM competency WHERE name = 'Teamwork & Collaboration'), 15, NULL, now(), NULL, now());

-- â”€â”€â”€ STAFF â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- Fixed UUIDs for easy reference in follow-up scripts.
-- Password for all seed accounts: Password@123

-- Managers first (no manager_id dependency)
INSERT INTO staff (id, name, email, password, role_id, manager_id, career_pathway_id,
                   account_status, is_first_login, is_deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('a1a1a1a1-0000-0000-0000-000000000001', 'Alice Johnson', 'alice.johnson@apex.dev',
     '$2a$10$z0nMRLOqlhZMiJ9DtwNktOIlG3J7e430wWvLOUdbl5aqQNYnVs8oi',
     (SELECT id FROM role WHERE name = 'IT Manager'), NULL, NULL,
     'ACTIVE', false, false, NULL, now(), NULL, now()),

    ('d4d4d4d4-0000-0000-0000-000000000001', 'David Lim', 'david.lim@apex.dev',
     '$2a$10$z0nMRLOqlhZMiJ9DtwNktOIlG3J7e430wWvLOUdbl5aqQNYnVs8oi',
     (SELECT id FROM role WHERE name = 'HR Manager'), NULL, NULL,
     'ACTIVE', false, false, NULL, now(), NULL, now()),

    ('f6f6f6f6-0000-0000-0000-000000000001', 'Frank Nguyen', 'frank.nguyen@apex.dev',
     '$2a$10$z0nMRLOqlhZMiJ9DtwNktOIlG3J7e430wWvLOUdbl5aqQNYnVs8oi',
     (SELECT id FROM role WHERE name = 'Sales Manager'), NULL, NULL,
     'ACTIVE', false, false, NULL, now(), NULL, now());

-- Non-managers (reference their manager's UUID)
INSERT INTO staff (id, name, email, password, role_id, manager_id, career_pathway_id,
                   account_status, is_first_login, is_deleted, created_by, created_at, updated_by, updated_at)
VALUES
    ('b2b2b2b2-0000-0000-0000-000000000001', 'Bob Smith', 'bob.smith@apex.dev',
     '$2a$10$z0nMRLOqlhZMiJ9DtwNktOIlG3J7e430wWvLOUdbl5aqQNYnVs8oi',
     (SELECT id FROM role WHERE name = 'Software Engineer'),
     'a1a1a1a1-0000-0000-0000-000000000001', NULL,
     'ACTIVE', false, false, NULL, now(), NULL, now()),

    ('c3c3c3c3-0000-0000-0000-000000000001', 'Carol Tan', 'carol.tan@apex.dev',
     '$2a$10$z0nMRLOqlhZMiJ9DtwNktOIlG3J7e430wWvLOUdbl5aqQNYnVs8oi',
     (SELECT id FROM role WHERE name = 'Software Engineer'),
     'a1a1a1a1-0000-0000-0000-000000000001', NULL,
     'ACTIVE', false, false, NULL, now(), NULL, now()),

    ('e5e5e5e5-0000-0000-0000-000000000001', 'Eve Chen', 'eve.chen@apex.dev',
     '$2a$10$z0nMRLOqlhZMiJ9DtwNktOIlG3J7e430wWvLOUdbl5aqQNYnVs8oi',
     (SELECT id FROM role WHERE name = 'HR Officer'),
     'd4d4d4d4-0000-0000-0000-000000000001', NULL,
     'ACTIVE', false, false, NULL, now(), NULL, now()),

    ('07070707-0000-0000-0000-000000000001', 'Grace Wong', 'grace.wong@apex.dev',
     '$2a$10$z0nMRLOqlhZMiJ9DtwNktOIlG3J7e430wWvLOUdbl5aqQNYnVs8oi',
     (SELECT id FROM role WHERE name = 'Sales Executive'),
     'f6f6f6f6-0000-0000-0000-000000000001', NULL,
     'ACTIVE', false, false, NULL, now(), NULL, now());

-- â”€â”€â”€ STAFF PROFILES â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

INSERT INTO staff_profile (staff_id, about, contact_number, profile_picture_path, created_by, created_at, updated_by, updated_at)
VALUES
    ('a1a1a1a1-0000-0000-0000-000000000001', 'IT Manager with 10 years of experience in enterprise software.', '+60112345678', NULL, NULL, now(), NULL, now()),
    ('d4d4d4d4-0000-0000-0000-000000000001', 'HR professional focused on talent development and culture.',     '+60123456789', NULL, NULL, now(), NULL, now()),
    ('f6f6f6f6-0000-0000-0000-000000000001', 'Sales leader with proven track record in B2B markets.',         '+60134567890', NULL, NULL, now(), NULL, now()),
    ('b2b2b2b2-0000-0000-0000-000000000001', 'Full-stack engineer specialising in cloud-native applications.', '+60145678901', NULL, NULL, now(), NULL, now()),
    ('c3c3c3c3-0000-0000-0000-000000000001', 'Backend engineer with strong interest in system design.',       '+60156789012', NULL, NULL, now(), NULL, now()),
    ('e5e5e5e5-0000-0000-0000-000000000001', 'HR Officer managing onboarding and employee relations.',        '+60167890123', NULL, NULL, now(), NULL, now()),
    ('07070707-0000-0000-0000-000000000001', 'Sales executive managing key accounts in the SME segment.',    '+60178901234', NULL, NULL, now(), NULL, now());

-- â”€â”€â”€ MANAGER HIERARCHY â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- The evaluation overview page fetches the staff list via manager_id (not org chart hierarchy),
-- so the top-level seed managers must report to superadmin for the dashboard to show data.
UPDATE staff
SET manager_id = (SELECT id FROM staff WHERE email = 'thongshuheng0330@gmail.com')
WHERE email IN ('alice.johnson@apex.dev', 'david.lim@apex.dev', 'frank.nguyen@apex.dev');

-- â”€â”€â”€ STAFF REFRESH TOKENS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- Required: auth service calls getStaffRefreshTokenById on login; row must exist.

INSERT INTO staff_refresh_token (staff_id, token, expires_at, created_at)
VALUES
    ('a1a1a1a1-0000-0000-0000-000000000001', NULL, now(), now()),
    ('d4d4d4d4-0000-0000-0000-000000000001', NULL, now(), now()),
    ('f6f6f6f6-0000-0000-0000-000000000001', NULL, now(), now()),
    ('b2b2b2b2-0000-0000-0000-000000000001', NULL, now(), now()),
    ('c3c3c3c3-0000-0000-0000-000000000001', NULL, now(), now()),
    ('e5e5e5e5-0000-0000-0000-000000000001', NULL, now(), now()),
    ('07070707-0000-0000-0000-000000000001', NULL, now(), now());

-- â”€â”€â”€ STAFF LOGIN AUDIT â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

INSERT INTO staff_login_audit (staff_id, last_login_at, last_login_failed_at, login_failed_attempts,
                               last_forgot_password_at, forgot_password_attempts, last_reset_password_at)
VALUES
    ('a1a1a1a1-0000-0000-0000-000000000001', NULL, NULL, 0, NULL, 0, NULL),
    ('d4d4d4d4-0000-0000-0000-000000000001', NULL, NULL, 0, NULL, 0, NULL),
    ('f6f6f6f6-0000-0000-0000-000000000001', NULL, NULL, 0, NULL, 0, NULL),
    ('b2b2b2b2-0000-0000-0000-000000000001', NULL, NULL, 0, NULL, 0, NULL),
    ('c3c3c3c3-0000-0000-0000-000000000001', NULL, NULL, 0, NULL, 0, NULL),
    ('e5e5e5e5-0000-0000-0000-000000000001', NULL, NULL, 0, NULL, 0, NULL),
    ('07070707-0000-0000-0000-000000000001', NULL, NULL, 0, NULL, 0, NULL);

-- â”€â”€â”€ EVALUATION CYCLE (1 closed) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

INSERT INTO evaluation_cycle (start_date, end_date, status, all_departments,
                              created_at, created_by, opened_at, closed_at, updated_at, updated_by)
VALUES ('2025-01-01', '2025-03-31', 'CLOSED', true,
        '2024-12-20 09:00:00', 'a1a1a1a1-0000-0000-0000-000000000001',
        '2025-01-01 08:00:00', '2025-04-01 08:00:00',
        '2025-04-01 08:00:00', 'a1a1a1a1-0000-0000-0000-000000000001');

-- â”€â”€â”€ EVALUATIONS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- overall_score formula: (sum(rating * weightage) * 100) / (sum(weightage) * 10)
-- All weightages per role sum to 100, so formula simplifies to sum(rating*w)/10.
--
-- Alice  (IT Manager):       8*25+7*25+8*20+9*30 = 805 â†’ score 80.5
-- Bob    (Software Engineer): 7*40+6*20+7*25+8*15 = 695 â†’ score 69.5
-- Carol  (Software Engineer): 9*40+7*20+8*25+7*15 = 805 â†’ score 80.5
-- David  (HR Manager):        8*25+8*35+8*20+7*20 = 780 â†’ score 78.0
-- Eve    (HR Officer):        7*35+8*25+6*20+7*20 = 705 â†’ score 70.5
-- Frank  (Sales Manager):     8*20+9*25+8*35+8*20 = 825 â†’ score 82.5
-- Grace  (Sales Executive):   7*30+8*35+7*20+8*15 = 750 â†’ score 75.0

INSERT INTO evaluation (staff_id, evaluation_cycle_id, comment, overall_score, created_at, created_by)
VALUES
    ('a1a1a1a1-0000-0000-0000-000000000001', 1, 'Strong technical leadership and mentorship of the team.',           80.5,  '2025-03-28 10:00:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('b2b2b2b2-0000-0000-0000-000000000001', 1, 'Solid engineer who consistently meets delivery targets.',           69.5,  '2025-03-28 10:15:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('c3c3c3c3-0000-0000-0000-000000000001', 1, 'Excellent problem-solver with a strong grasp of system design.',    80.5,  '2025-03-28 10:30:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('d4d4d4d4-0000-0000-0000-000000000001', 1, 'Effective people manager; drives a positive team culture.',         78.0,  '2025-03-28 11:00:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('e5e5e5e5-0000-0000-0000-000000000001', 1, 'Reliable and empathetic; strong at employee onboarding.',           70.5,  '2025-03-28 11:15:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('f6f6f6f6-0000-0000-0000-000000000001', 1, 'Exceptional sales acumen; exceeded Q1 revenue target by 18%.',      82.5,  '2025-03-28 12:00:00', 'f6f6f6f6-0000-0000-0000-000000000001'),
    ('07070707-0000-0000-0000-000000000001', 1, 'Strong client relationships; consistently hits monthly quota.',     75.0,  '2025-03-28 12:15:00', 'f6f6f6f6-0000-0000-0000-000000000001');

-- â”€â”€â”€ EVALUATION RATINGS â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

INSERT INTO evaluation_ratings (evaluation_id, comp_id, rating, created_at)
VALUES
    -- Alice (IT Manager) â€“ evaluation_id=1
    (1, (SELECT id FROM competency WHERE name = 'Technical Proficiency'),    8, '2025-03-28'),
    (1, (SELECT id FROM competency WHERE name = 'Communication Skills'),     7, '2025-03-28'),
    (1, (SELECT id FROM competency WHERE name = 'Problem Solving'),          8, '2025-03-28'),
    (1, (SELECT id FROM competency WHERE name = 'Leadership & Initiative'),  9, '2025-03-28'),

    -- Bob (Software Engineer) â€“ evaluation_id=2
    (2, (SELECT id FROM competency WHERE name = 'Technical Proficiency'),    7, '2025-03-28'),
    (2, (SELECT id FROM competency WHERE name = 'Communication Skills'),     6, '2025-03-28'),
    (2, (SELECT id FROM competency WHERE name = 'Problem Solving'),          7, '2025-03-28'),
    (2, (SELECT id FROM competency WHERE name = 'Teamwork & Collaboration'), 8, '2025-03-28'),

    -- Carol (Software Engineer) â€“ evaluation_id=3
    (3, (SELECT id FROM competency WHERE name = 'Technical Proficiency'),    9, '2025-03-28'),
    (3, (SELECT id FROM competency WHERE name = 'Communication Skills'),     7, '2025-03-28'),
    (3, (SELECT id FROM competency WHERE name = 'Problem Solving'),          8, '2025-03-28'),
    (3, (SELECT id FROM competency WHERE name = 'Teamwork & Collaboration'), 7, '2025-03-28'),

    -- David (HR Manager) â€“ evaluation_id=4
    (4, (SELECT id FROM competency WHERE name = 'Communication Skills'),     8, '2025-03-28'),
    (4, (SELECT id FROM competency WHERE name = 'Leadership & Initiative'),  8, '2025-03-28'),
    (4, (SELECT id FROM competency WHERE name = 'Teamwork & Collaboration'), 8, '2025-03-28'),
    (4, (SELECT id FROM competency WHERE name = 'Problem Solving'),          7, '2025-03-28'),

    -- Eve (HR Officer) â€“ evaluation_id=5
    (5, (SELECT id FROM competency WHERE name = 'Communication Skills'),     7, '2025-03-28'),
    (5, (SELECT id FROM competency WHERE name = 'Teamwork & Collaboration'), 8, '2025-03-28'),
    (5, (SELECT id FROM competency WHERE name = 'Problem Solving'),          6, '2025-03-28'),
    (5, (SELECT id FROM competency WHERE name = 'Customer Orientation'),     7, '2025-03-28'),

    -- Frank (Sales Manager) â€“ evaluation_id=6
    (6, (SELECT id FROM competency WHERE name = 'Communication Skills'),     8, '2025-03-28'),
    (6, (SELECT id FROM competency WHERE name = 'Customer Orientation'),     9, '2025-03-28'),
    (6, (SELECT id FROM competency WHERE name = 'Leadership & Initiative'),  8, '2025-03-28'),
    (6, (SELECT id FROM competency WHERE name = 'Problem Solving'),          8, '2025-03-28'),

    -- Grace (Sales Executive) â€“ evaluation_id=7
    (7, (SELECT id FROM competency WHERE name = 'Communication Skills'),     7, '2025-03-28'),
    (7, (SELECT id FROM competency WHERE name = 'Customer Orientation'),     8, '2025-03-28'),
    (7, (SELECT id FROM competency WHERE name = 'Problem Solving'),          7, '2025-03-28'),
    (7, (SELECT id FROM competency WHERE name = 'Teamwork & Collaboration'), 8, '2025-03-28');
