-- Demo staff for testing SOP training: Assign Training, the Learning Portal and the scoreboard.
-- Run after seed_superadmin.sql:
--   psql -U postgres -d apex_db -f seed_demo_training.sql
-- Safe to re-run: rows that already exist are skipped. Every account's password is Demo@1234.
--
--   trainer@tbm.net   Tan Wei Ming     Trainer                  HR Department          manages training
--   hr@tbm.net        Nadia Rahman     HR Executive             HR Department          assigns any course
--   jason@tbm.net     Jason Lim        Outlet Supervisor        Sales Department
--   aisyah@tbm.net    Nur Aisyah       Sales Executive          Sales Department       reports to Jason
--   ravi@tbm.net      Ravi Kumar       Sales Executive          Sales Department       reports to Jason
--   sarah@tbm.net     Sarah Wong       Front Office Supervisor  Front Office Department
--   meiling@tbm.net   Chong Mei Ling   Receptionist             Front Office Department reports to Sarah
--   farid@tbm.net     Ahmad Farid      Receptionist             Front Office Department reports to Sarah

BEGIN;

CREATE TEMP TABLE demo_staff (name text, email text, role text) ON COMMIT DROP;
INSERT INTO demo_staff VALUES
  ('Tan Wei Ming',   'trainer@tbm.net', 'Trainer'),
  ('Nadia Rahman',   'hr@tbm.net',      'HR Executive'),
  ('Jason Lim',      'jason@tbm.net',   'Outlet Supervisor'),
  ('Nur Aisyah',     'aisyah@tbm.net',  'Sales Executive'),
  ('Ravi Kumar',     'ravi@tbm.net',    'Sales Executive'),
  ('Sarah Wong',     'sarah@tbm.net',   'Front Office Supervisor'),
  ('Chong Mei Ling', 'meiling@tbm.net', 'Receptionist'),
  ('Ahmad Farid',    'farid@tbm.net',   'Receptionist');

-- Departments are org-chart nodes of type 'D'.
INSERT INTO org_chart (name, type, is_root, is_deleted, created_at, updated_at)
SELECT d.name, 'D', false, false, now(), now()
FROM (VALUES ('HR Department'), ('Sales Department'), ('Front Office Department')) AS d(name)
WHERE NOT EXISTS (SELECT 1 FROM org_chart o WHERE o.name = d.name AND NOT o.is_deleted);

-- Job roles, each belonging to a department.
INSERT INTO role (name, description, is_visible, is_deleted, org_chart_id, created_at, updated_at)
SELECT r.name, r.description, true, false,
       (SELECT o.id FROM org_chart o WHERE o.name = r.dept AND NOT o.is_deleted ORDER BY o.id LIMIT 1), now(), now()
FROM (VALUES
  ('Trainer',                 'Creates, reviews and assigns SOP training', 'HR Department'),
  ('HR Executive',            'Assigns training across the company',       'HR Department'),
  ('Outlet Supervisor',       'Supervises an outlet sales team',           'Sales Department'),
  ('Sales Executive',         'Sells and hands over products at outlets',  'Sales Department'),
  ('Front Office Supervisor', 'Supervises the front office',               'Front Office Department'),
  ('Receptionist',            'Front desk, payments and cash handling',    'Front Office Department')
) AS r(name, description, dept)
WHERE NOT EXISTS (SELECT 1 FROM role x WHERE x.name = r.name AND NOT x.is_deleted);

-- Permissions: everyone can sign in; the Trainer manages training; HR may assign any course.
INSERT INTO role_authority (role_id, authority_id, created_at, updated_at)
SELECT r.id, a.id, now(), now()
FROM (VALUES
  ('Trainer', 'ROLE_USER'), ('Trainer', 'CAN_MANAGE_TRAINING'),
  ('HR Executive', 'ROLE_USER'), ('HR Executive', 'CAN_ASSIGN_TRAINING'), ('HR Executive', 'CAN_VIEW_STAFF'),
  ('Outlet Supervisor', 'ROLE_USER'), ('Sales Executive', 'ROLE_USER'),
  ('Front Office Supervisor', 'ROLE_USER'), ('Receptionist', 'ROLE_USER')
) AS p(role, authority)
JOIN role r ON r.name = p.role AND NOT r.is_deleted
JOIN authority a ON a.name = p.authority
ON CONFLICT (role_id, authority_id) DO NOTHING;

-- Accounts, ready to sign in (no first-login OTP). Password: Demo@1234
INSERT INTO staff (id, name, email, role_id, account_status, is_first_login, is_deleted, password, created_at, updated_at)
SELECT gen_random_uuid(), d.name, d.email,
       (SELECT r.id FROM role r WHERE r.name = d.role AND NOT r.is_deleted ORDER BY r.id LIMIT 1),
       'ACTIVE', false, false, '$2a$10$GxYm.efcq0yRt1yWnxNXmuRwkQsxwRPfLwRMk/zCjB3LR1gNYsqay', now(), now()
FROM demo_staff d
WHERE NOT EXISTS (SELECT 1 FROM staff s WHERE s.email = d.email);

-- Superiors: the sales team reports to Jason, the receptionists to Sarah.
UPDATE staff SET manager_id = (SELECT id FROM staff WHERE email = 'jason@tbm.net')
WHERE email IN ('aisyah@tbm.net', 'ravi@tbm.net') AND manager_id IS NULL;
UPDATE staff SET manager_id = (SELECT id FROM staff WHERE email = 'sarah@tbm.net')
WHERE email IN ('meiling@tbm.net', 'farid@tbm.net') AND manager_id IS NULL;

-- Rows the login flow reads for every account.
INSERT INTO staff_profile (staff_id, created_at, updated_at)
SELECT s.id, now(), now() FROM staff s JOIN demo_staff d ON d.email = s.email
ON CONFLICT (staff_id) DO NOTHING;

INSERT INTO staff_login_audit (staff_id, login_failed_attempts, forgot_password_attempts)
SELECT s.id, 0, 0 FROM staff s JOIN demo_staff d ON d.email = s.email
ON CONFLICT (staff_id) DO NOTHING;

INSERT INTO staff_refresh_token (staff_id, created_at, expires_at, token)
SELECT s.id, now(), now() + interval '7 days', NULL FROM staff s JOIN demo_staff d ON d.email = s.email
ON CONFLICT (staff_id) DO NOTHING;

COMMIT;
