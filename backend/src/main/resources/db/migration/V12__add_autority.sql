-- add new authority
INSERT INTO authority (name, description_key, label_key)
VALUES
    ('CAN_MANAGE_TRAINING', 'auth.can.manage.training.desc', 'auth.can.manage.training'),
    ('CAN_ASSIGN_TRAINING', 'auth.can.assign.training.desc', 'auth.can.assign.training'),
    ('CAN_MANAGE_LEARNING_MATERIAL', 'auth.can.manage.learning.material.desc', 'auth.can.manage.learning.material'),
    ('CAN_MANAGE_EVALUATION', 'auth.can.manage.evaluation.desc', 'auth.can.manage.evaluation');

