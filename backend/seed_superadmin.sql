-- ============================================================================
-- APEX local seed: superadmin login + supporting rows.
-- Needed because Flyway is OFF (no V1 base migration) and the app does not
-- auto-seed. Hibernate (ddl-auto:update) builds the schema; this script seeds
-- the minimum data to log in as superadmin and exercise the existing modules.
--
-- Mirrors teammate's DB (authority / org_chart / role / role_authority) plus a
-- ready-to-use staff account (is_first_login=false, BCrypt password) and the
-- login_audit + refresh_token rows the /auth/login flow requires.
--
-- Login:  superadmin@tbm.net  /  Admin@1234
-- Run:    psql -U postgres -d apex_db -f backend/seed_superadmin.sql
-- ============================================================================
BEGIN;

-- Org chart nodes (superadmin root node + a few departments) -------------------
INSERT INTO org_chart (id, name, type, is_root, is_deleted, created_at, updated_at) VALUES
  (1, 'superadmin',      'P', false, false, now(), now()),
  (2, 'IT Department',   'D', false, false, now(), now()),
  (3, 'HR Department',   'D', false, false, now(), now()),
  (4, 'Sales Department','D', false, false, now(), now())
ON CONFLICT (id) DO NOTHING;

-- Superadmin role (backend hardcodes SUPERADMIN_ROLE_ID = 1) -------------------
INSERT INTO role (id, name, description, is_visible, is_deleted, org_chart_id, created_at, updated_at) VALUES
  (1, 'superadmin', 'Super administrator', false, false, 1, now(), now())
ON CONFLICT (id) DO NOTHING;

-- Authorities (transcribed from teammate's authority table) --------------------
INSERT INTO authority (id, name, description_key, label_key) VALUES
  (3,  'CAN_MANAGE_STAFF',             'auth.can.manage.staff.desc',             'auth.can.manage.staff'),
  (4,  'CAN_MANAGE_EVALUATION_CYCLE',  'auth.can.manage.evaluation.cycle.desc',  'auth.can.manage.evaluation.cycle'),
  (5,  'CAN_MANAGE_EVALUATION',        'auth.can.manage.evaluation.desc',        'auth.can.manage.evaluation'),
  (6,  'CAN_VIEW_ACCESS_CONTROL',      'auth.can.view.access.control.desc',      'auth.can.view.access.control'),
  (7,  'CAN_MANAGE_ORG_CHART',         'auth.can.manage.org.chart.desc',         'auth.can.manage.org.chart.label'),
  (8,  'CAN_VIEW_STAFF',               'auth.can.view.staff.desc',               'auth.can.view.staff'),
  (9,  'CAN_MANAGE_ROLE',              'auth.can.manage.role.desc',              'auth.can.manage.role'),
  (10, 'CAN_MANAGE_COMPETENCY',        'auth.can.manage.competency.desc',        'auth.can.manage.competency'),
  (11, 'CAN_MANAGE_ACCESS_CONTROL',    'auth.can.manage.access.control.desc',    'auth.can.manage.access.control'),
  (12, 'ROLE_USER',                    'auth.role.user.desc',                    'auth.role.user'),
  (15, 'CAN_MANAGE_CAREER_PATHWAY',    'auth.can.manage.career.pathway.desc',    'auth.can.manage.career.pathway.label'),
  (17, 'CAN_MANAGE_TRAINING',          'auth.can.manage.training.desc',          'auth.can.manage.training'),
  (18, 'CAN_ASSIGN_TRAINING',          'auth.can.assign.training.desc',          'auth.can.assign.training'),
  (19, 'CAN_MANAGE_LEARNING_MATERIAL', 'auth.can.manage.learning.material.desc', 'auth.can.manage.learning.material')
ON CONFLICT (id) DO NOTHING;

-- Grant ALL authorities to superadmin -----------------------------------------
INSERT INTO role_authority (role_id, authority_id, created_at, updated_at)
  SELECT 1, a.id, now(), now() FROM authority a
ON CONFLICT (role_id, authority_id) DO NOTHING;

-- Keep identity sequences ahead of the explicit IDs we just inserted ----------
SELECT setval(pg_get_serial_sequence('org_chart','id'), (SELECT MAX(id) FROM org_chart));
SELECT setval(pg_get_serial_sequence('role','id'),      (SELECT MAX(id) FROM role));
SELECT setval(pg_get_serial_sequence('authority','id'), (SELECT MAX(id) FROM authority));

-- Superadmin staff account: ready to log in (no first-login/OTP flow) ----------
-- Password 'Admin@1234' hashed with BCrypt (cost 10).
INSERT INTO staff (id, name, email, role_id, account_status, is_first_login, is_deleted, password, created_at, updated_at)
  SELECT gen_random_uuid(), 'superadmin', 'superadmin@tbm.net', 1, 'ACTIVE', false, false,
         '$2b$10$0ja2OgAjPkWz804G4oerTe7eskEnIJUgzFretTbB5R3pwLkO/PCL6', now(), now()
  WHERE NOT EXISTS (SELECT 1 FROM staff WHERE email = 'superadmin@tbm.net');

-- Supporting rows the /auth/login flow reads ----------------------------------
INSERT INTO staff_profile (staff_id, created_at, updated_at)
  SELECT id, now(), now() FROM staff WHERE email = 'superadmin@tbm.net'
ON CONFLICT (staff_id) DO NOTHING;

INSERT INTO staff_login_audit (staff_id, login_failed_attempts, forgot_password_attempts)
  SELECT id, 0, 0 FROM staff WHERE email = 'superadmin@tbm.net'
ON CONFLICT (staff_id) DO NOTHING;

INSERT INTO staff_refresh_token (staff_id, created_at, expires_at, token)
  SELECT id, now(), now() + interval '7 days', NULL FROM staff WHERE email = 'superadmin@tbm.net'
ON CONFLICT (staff_id) DO NOTHING;

COMMIT;
