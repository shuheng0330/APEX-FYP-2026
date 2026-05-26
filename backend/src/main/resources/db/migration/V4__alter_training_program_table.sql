ALTER TABLE training_program
    ADD COLUMN location_name VARCHAR(255),
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION,
    ADD COLUMN checkin_radius_meters DOUBLE PRECISION;

CREATE TABLE training_attendance (
                                     attendance_id SERIAL PRIMARY KEY,
                                     training_id BIGINT NOT NULL,
                                     staff_id UUID NOT NULL,
                                     check_in_time TIMESTAMP,
                                     check_in_latitude DOUBLE PRECISION,
                                     check_in_longitude DOUBLE PRECISION,
                                     within_geofence BOOLEAN,

                                     CONSTRAINT fk_training FOREIGN KEY (training_id)
                                         REFERENCES training_program (training_id)
                                         ON DELETE CASCADE,

                                     CONSTRAINT fk_staff FOREIGN KEY (staff_id)
                                         REFERENCES staff (id)
                                         ON DELETE CASCADE
);
