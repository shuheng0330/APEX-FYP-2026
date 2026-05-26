ALTER TABLE staff
    ADD COLUMN password VARCHAR(255);

UPDATE staff
SET is_first_login = true;

-- split the staff table for easy maintenance
CREATE TABLE staff_login_audit
(
    staff_id                 uuid PRIMARY KEY REFERENCES staff (id) ON DELETE CASCADE,
    last_login_at            timestamptz,
    last_login_failed_at     timestamptz,
    login_failed_attempts    int NOT NULL DEFAULT 0,
    last_forgot_password_at  timestamptz,
    forgot_password_attempts int NOT NULL DEFAULT 0,
    last_reset_password_at   timestamptz
);

INSERT INTO staff_login_audit (staff_id, last_login_at, last_login_failed_at, login_failed_attempts,
                               last_forgot_password_at, forgot_password_attempts, last_reset_password_at)
SELECT id,
       last_login_at,
       last_login_failed_at,
       login_failed_attempts,
       last_forgot_password_at,
       forgot_password_attempts,
       last_reset_password_at
FROM staff;

-- drop audit columns from staff
ALTER TABLE staff
DROP
COLUMN last_login_at,
    DROP
COLUMN last_login_failed_at,
    DROP
COLUMN login_failed_attempts,
    DROP
COLUMN last_forgot_password_at,
    DROP
COLUMN forgot_password_attempts,
    DROP
COLUMN last_reset_password_at;


-- table for refresh_token
CREATE TABLE staff_refresh_token
(
    staff_id   UUID PRIMARY KEY REFERENCES staff (id) ON DELETE CASCADE,
    token      VARCHAR(255) UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- table for otp
CREATE TABLE staff_otp
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    staff_id   UUID        NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    otp_code   VARCHAR(10) NOT NULL,
    purpose    VARCHAR(50) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
