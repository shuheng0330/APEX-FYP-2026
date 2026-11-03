-- Requests for Trainer access after a Product Training (FR-15-06): Hibernate creates the table. One request per
-- person per session, as in P008 to P010.
CREATE UNIQUE INDEX IF NOT EXISTS uq_training_access_request ON training_access_request (staff_id, training_id);
