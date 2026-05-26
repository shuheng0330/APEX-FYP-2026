-- CREATE staff_profile table
CREATE TABLE staff_profile
(
    staff_id             UUID PRIMARY KEY,
    about                VARCHAR(1000),
    contact_number       VARCHAR(15),
    profile_picture_path VARCHAR(1000),
    created_by           UUID,
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_by           UUID,
    updated_at           TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT fk_staff_profile_created_by FOREIGN KEY (created_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_staff_profile_updated_by FOREIGN KEY (updated_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_staff_profile_staff_id FOREIGN KEY (staff_id)
        REFERENCES staff (id) ON DELETE CASCADE
);

-- INSERT DATA TO PREVENT ERROR
INSERT INTO staff_profile (staff_id,
                           about,
                           contact_number,
                           profile_picture_path,
                           created_by,
                           created_at,
                           updated_by,
                           updated_at)
SELECT s.id                          AS staff_id,
       NULL                          AS about,
       NULL                          AS contact_number,
       NULL                          AS profile_picture_path,
       s.created_by,
       COALESCE(s.created_at, NOW()) AS created_at,
       s.updated_by,
       COALESCE(s.updated_at, NOW()) AS updated_at
FROM staff s
WHERE s.is_deleted = FALSE
  AND s.id NOT IN (SELECT staff_id FROM staff_profile);

-- Create staff_self_declared_skill table
CREATE TABLE staff_self_declared_skill
(
    id          BIGSERIAL PRIMARY KEY,
    staff_id    UUID         NOT NULL,
    skill       VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    proficiency VARCHAR(50)  NOT NULL,
    created_by  UUID,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_by  UUID,
    updated_at  TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT fk_ssds_created_by FOREIGN KEY (created_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_ssds_updated_by FOREIGN KEY (updated_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_ssds_staff_id FOREIGN KEY (staff_id)
        REFERENCES staff (id) ON DELETE CASCADE
);


CREATE TABLE staff_cert
(
    id          BIGSERIAL PRIMARY KEY,
    staff_id    UUID NOT NULL,
    cert_name   VARCHAR(255),
    file_name   VARCHAR(255),
    description VARCHAR(1000),
    cert_path   VARCHAR(1000),
    created_by  UUID,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_by  UUID,
    updated_at  TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT fk_staff_cert_created_by FOREIGN KEY (created_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_staff_cert_updated_by FOREIGN KEY (updated_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_staff_cert_staff_id FOREIGN KEY (staff_id)
        REFERENCES staff (id) ON DELETE CASCADE
);
