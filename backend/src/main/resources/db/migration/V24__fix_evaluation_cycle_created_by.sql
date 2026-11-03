-- Re-attribute seed evaluation cycles from IT Manager (Alice) to the superadmin.
-- V21 and V22 cannot be edited (Flyway checksum), so the fix lives here.
UPDATE evaluation_cycle
SET created_by = (SELECT id FROM staff WHERE email = 'thongshuheng0330@gmail.com'),
    updated_by = (SELECT id FROM staff WHERE email = 'thongshuheng0330@gmail.com')
WHERE created_by = 'a1a1a1a1-0000-0000-0000-000000000001'::uuid;
