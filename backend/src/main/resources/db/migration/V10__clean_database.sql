-- clean all tables
DO
$$
BEGIN
EXECUTE (SELECT 'TRUNCATE TABLE ' || string_agg(quote_ident(tablename), ', ') || ' RESTART IDENTITY CASCADE'
         FROM pg_tables
         WHERE schemaname = 'public'
           AND tablename NOT IN ('authority', 'flyway_schema_history'));
END $$;

-- clean constraint associate with supabase.user table
DO
$$
DECLARE
r RECORD;
BEGIN
FOR r IN
SELECT conname, conrelid::regclass AS table_name
FROM pg_constraint
WHERE confrelid = 'auth.users'::regclass
    LOOP
        EXECUTE 'ALTER TABLE ' || r.table_name || ' DROP CONSTRAINT ' || r.conname || ';';
END LOOP;
END $$;

-- remove unnecessary authority
DELETE
FROM authority
WHERE name = 'CAN_VIEW_CAREER_PATHWAY';

-- create superadmin
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

INSERT INTO staff (id, name, email, role_id, account_status, is_first_login, is_deleted, created_by, created_at,
                   updated_by, updated_at)
VALUES (gen_random_uuid(),
        'superadmin',
        'superadmin@tbm.net',
        (SELECT id FROM role WHERE name = 'superadmin'),
        'ACTIVE',
        true,
        false,
        NULL,
        now(),
        NULL,
        now());

INSERT INTO staff_profile (staff_id, created_by, created_at, updated_by, updated_at)
VALUES ((SELECT id FROM staff WHERE name = 'superadmin'),
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
