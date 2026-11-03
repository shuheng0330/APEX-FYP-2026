-- V22__seed_historical_evaluation_data.sql
-- Historical seed: 4 closed annual evaluation cycles for 2021-2024.
-- Adds evaluations and competency ratings for the 7 V21 seed staff to support
-- 1Y / 3Y / 5Y trend dashboards and appraisal readiness calculations.

-- EVALUATION CYCLES -------------------------------------------------------------------------

INSERT INTO evaluation_cycle (start_date, end_date, status, all_departments,
                              created_at, created_by, opened_at, closed_at, updated_at, updated_by)
VALUES
    ('2021-01-01', '2021-12-31', 'CLOSED', true,
     '2020-12-20 09:00:00', 'a1a1a1a1-0000-0000-0000-000000000001',
     '2021-01-01 08:00:00', '2022-01-03 08:00:00',
     '2022-01-03 08:00:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('2022-01-01', '2022-12-31', 'CLOSED', true,
     '2021-12-20 09:00:00', 'a1a1a1a1-0000-0000-0000-000000000001',
     '2022-01-01 08:00:00', '2023-01-03 08:00:00',
     '2023-01-03 08:00:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('2023-01-01', '2023-12-31', 'CLOSED', true,
     '2022-12-20 09:00:00', 'a1a1a1a1-0000-0000-0000-000000000001',
     '2023-01-01 08:00:00', '2024-01-03 08:00:00',
     '2024-01-03 08:00:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('2024-01-01', '2024-12-31', 'CLOSED', true,
     '2023-12-20 09:00:00', 'a1a1a1a1-0000-0000-0000-000000000001',
     '2024-01-01 08:00:00', '2025-01-03 08:00:00',
     '2025-01-03 08:00:00', 'a1a1a1a1-0000-0000-0000-000000000001');

-- EVALUATIONS -------------------------------------------------------------------------------
-- Scores are calculated from the role competency weights in V21:
-- overall_score = sum(rating * weightage) / 10.

INSERT INTO evaluation (staff_id, evaluation_cycle_id, comment, overall_score, created_at, created_by)
SELECT e.staff_id::uuid,
       ec.id,
       e.comment,
       e.overall_score,
       e.created_at::timestamp,
       e.created_by::uuid
FROM (VALUES
    -- 2021 baseline year
    ('a1a1a1a1-0000-0000-0000-000000000001', '2021-01-01'::date, 'Good foundation in technical leadership; opportunity to improve cross-team communication.', 70.5, '2021-12-28 10:00:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2021-01-01'::date, 'Developing steadily as an engineer; needs more consistency in problem analysis.', 61.5, '2021-12-28 10:15:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2021-01-01'::date, 'Strong technical potential with solid teamwork across delivery tasks.', 70.0, '2021-12-28 10:30:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2021-01-01'::date, 'Reliable HR leadership with room to strengthen initiative on strategic programs.', 69.5, '2021-12-28 11:00:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2021-01-01'::date, 'Dependable HR support; communication and onboarding execution are improving.', 63.5, '2021-12-28 11:15:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2021-01-01'::date, 'Good sales management foundation with strong customer orientation.', 73.0, '2021-12-28 12:00:00', 'f6f6f6f6-0000-0000-0000-000000000001'),
    ('07070707-0000-0000-0000-000000000001', '2021-01-01'::date, 'Building client confidence and improving teamwork across account activities.', 65.5, '2021-12-28 12:15:00', 'f6f6f6f6-0000-0000-0000-000000000001'),

    -- 2022 improvement year
    ('a1a1a1a1-0000-0000-0000-000000000001', '2022-01-01'::date, 'Improved stakeholder communication and stronger ownership of technology planning.', 75.5, '2022-12-28 10:00:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2022-01-01'::date, 'More reliable delivery and better collaboration with product stakeholders.', 65.5, '2022-12-28 10:15:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2022-01-01'::date, 'Consistent technical growth with stronger problem-solving discipline.', 74.5, '2022-12-28 10:30:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2022-01-01'::date, 'Improved team coaching and stronger communication during HR initiatives.', 73.5, '2022-12-28 11:00:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2022-01-01'::date, 'Clear improvement in employee support and onboarding follow-through.', 67.5, '2022-12-28 11:15:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2022-01-01'::date, 'Stronger sales leadership and improved coaching of account planning.', 77.0, '2022-12-28 12:00:00', 'f6f6f6f6-0000-0000-0000-000000000001'),
    ('07070707-0000-0000-0000-000000000001', '2022-01-01'::date, 'Improved communication with clients and more consistent quota delivery.', 69.0, '2022-12-28 12:15:00', 'f6f6f6f6-0000-0000-0000-000000000001'),

    -- 2023 consolidation year
    ('a1a1a1a1-0000-0000-0000-000000000001', '2023-01-01'::date, 'Strong leadership maturity and better mentoring across engineering teams.', 78.0, '2023-12-28 10:00:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2023-01-01'::date, 'Solid improvement in problem solving and dependable delivery quality.', 67.5, '2023-12-28 10:15:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2023-01-01'::date, 'High technical consistency and increasingly confident system design decisions.', 77.0, '2023-12-28 10:30:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2023-01-01'::date, 'Effective HR leadership and stronger execution on people operations.', 76.0, '2023-12-28 11:00:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2023-01-01'::date, 'Reliable HR officer with better judgment on employee relations cases.', 69.5, '2023-12-28 11:15:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2023-01-01'::date, 'Strong sales results and improved strategic account leadership.', 79.5, '2023-12-28 12:00:00', 'f6f6f6f6-0000-0000-0000-000000000001'),
    ('07070707-0000-0000-0000-000000000001', '2023-01-01'::date, 'Good account ownership and stronger client relationship management.', 72.0, '2023-12-28 12:15:00', 'f6f6f6f6-0000-0000-0000-000000000001'),

    -- 2024 latest full year before the V21 2025 Q1 cycle
    ('a1a1a1a1-0000-0000-0000-000000000001', '2024-01-01'::date, 'Very strong leadership and sustained impact on department delivery.', 79.5, '2024-12-28 10:00:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2024-01-01'::date, 'Continued growth with more ownership of technical delivery outcomes.', 68.5, '2024-12-28 10:15:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2024-01-01'::date, 'Strong technical contributor with clear readiness for larger scope.', 79.0, '2024-12-28 10:30:00', 'a1a1a1a1-0000-0000-0000-000000000001'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2024-01-01'::date, 'Strong HR manager with consistent team leadership and people impact.', 77.5, '2024-12-28 11:00:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2024-01-01'::date, 'Improved HR operations support with stronger collaboration across teams.', 70.5, '2024-12-28 11:15:00', 'd4d4d4d4-0000-0000-0000-000000000001'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2024-01-01'::date, 'Excellent sales leadership and sustained customer-focused performance.', 81.0, '2024-12-28 12:00:00', 'f6f6f6f6-0000-0000-0000-000000000001'),
    ('07070707-0000-0000-0000-000000000001', '2024-01-01'::date, 'Consistent sales contributor with improved quota achievement and teamwork.', 73.5, '2024-12-28 12:15:00', 'f6f6f6f6-0000-0000-0000-000000000001')
) AS e(staff_id, cycle_start_date, comment, overall_score, created_at, created_by)
JOIN evaluation_cycle ec ON ec.start_date = e.cycle_start_date;

-- EVALUATION RATINGS ------------------------------------------------------------------------

INSERT INTO evaluation_ratings (evaluation_id, comp_id, rating, created_at)
SELECT ev.evaluation_id,
       c.id,
       r.rating,
       r.created_at::date
FROM (VALUES
    -- 2021
    ('a1a1a1a1-0000-0000-0000-000000000001', '2021-01-01'::date, 'Technical Proficiency', 7, '2021-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2021-01-01'::date, 'Communication Skills', 6, '2021-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2021-01-01'::date, 'Problem Solving', 7, '2021-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2021-01-01'::date, 'Leadership & Initiative', 8, '2021-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2021-01-01'::date, 'Technical Proficiency', 6, '2021-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2021-01-01'::date, 'Communication Skills', 5, '2021-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2021-01-01'::date, 'Problem Solving', 6, '2021-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2021-01-01'::date, 'Teamwork & Collaboration', 7, '2021-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2021-01-01'::date, 'Technical Proficiency', 8, '2021-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2021-01-01'::date, 'Communication Skills', 6, '2021-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2021-01-01'::date, 'Problem Solving', 7, '2021-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2021-01-01'::date, 'Teamwork & Collaboration', 6, '2021-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2021-01-01'::date, 'Communication Skills', 7, '2021-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2021-01-01'::date, 'Leadership & Initiative', 7, '2021-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2021-01-01'::date, 'Teamwork & Collaboration', 7, '2021-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2021-01-01'::date, 'Problem Solving', 6, '2021-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2021-01-01'::date, 'Communication Skills', 6, '2021-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2021-01-01'::date, 'Teamwork & Collaboration', 7, '2021-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2021-01-01'::date, 'Problem Solving', 5, '2021-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2021-01-01'::date, 'Customer Orientation', 7, '2021-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2021-01-01'::date, 'Communication Skills', 7, '2021-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2021-01-01'::date, 'Customer Orientation', 8, '2021-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2021-01-01'::date, 'Leadership & Initiative', 7, '2021-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2021-01-01'::date, 'Problem Solving', 7, '2021-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2021-01-01'::date, 'Communication Skills', 6, '2021-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2021-01-01'::date, 'Customer Orientation', 7, '2021-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2021-01-01'::date, 'Problem Solving', 6, '2021-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2021-01-01'::date, 'Teamwork & Collaboration', 7, '2021-12-28'),

    -- 2022
    ('a1a1a1a1-0000-0000-0000-000000000001', '2022-01-01'::date, 'Technical Proficiency', 8, '2022-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2022-01-01'::date, 'Communication Skills', 7, '2022-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2022-01-01'::date, 'Problem Solving', 7, '2022-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2022-01-01'::date, 'Leadership & Initiative', 8, '2022-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2022-01-01'::date, 'Technical Proficiency', 7, '2022-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2022-01-01'::date, 'Communication Skills', 6, '2022-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2022-01-01'::date, 'Problem Solving', 6, '2022-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2022-01-01'::date, 'Teamwork & Collaboration', 7, '2022-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2022-01-01'::date, 'Technical Proficiency', 8, '2022-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2022-01-01'::date, 'Communication Skills', 6, '2022-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2022-01-01'::date, 'Problem Solving', 8, '2022-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2022-01-01'::date, 'Teamwork & Collaboration', 7, '2022-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2022-01-01'::date, 'Communication Skills', 8, '2022-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2022-01-01'::date, 'Leadership & Initiative', 7, '2022-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2022-01-01'::date, 'Teamwork & Collaboration', 7, '2022-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2022-01-01'::date, 'Problem Solving', 7, '2022-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2022-01-01'::date, 'Communication Skills', 7, '2022-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2022-01-01'::date, 'Teamwork & Collaboration', 7, '2022-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2022-01-01'::date, 'Problem Solving', 6, '2022-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2022-01-01'::date, 'Customer Orientation', 7, '2022-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2022-01-01'::date, 'Communication Skills', 8, '2022-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2022-01-01'::date, 'Customer Orientation', 8, '2022-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2022-01-01'::date, 'Leadership & Initiative', 8, '2022-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2022-01-01'::date, 'Problem Solving', 7, '2022-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2022-01-01'::date, 'Communication Skills', 7, '2022-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2022-01-01'::date, 'Customer Orientation', 7, '2022-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2022-01-01'::date, 'Problem Solving', 7, '2022-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2022-01-01'::date, 'Teamwork & Collaboration', 7, '2022-12-28'),

    -- 2023
    ('a1a1a1a1-0000-0000-0000-000000000001', '2023-01-01'::date, 'Technical Proficiency', 8, '2023-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2023-01-01'::date, 'Communication Skills', 7, '2023-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2023-01-01'::date, 'Problem Solving', 8, '2023-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2023-01-01'::date, 'Leadership & Initiative', 8, '2023-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2023-01-01'::date, 'Technical Proficiency', 7, '2023-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2023-01-01'::date, 'Communication Skills', 6, '2023-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2023-01-01'::date, 'Problem Solving', 7, '2023-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2023-01-01'::date, 'Teamwork & Collaboration', 7, '2023-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2023-01-01'::date, 'Technical Proficiency', 8, '2023-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2023-01-01'::date, 'Communication Skills', 7, '2023-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2023-01-01'::date, 'Problem Solving', 8, '2023-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2023-01-01'::date, 'Teamwork & Collaboration', 7, '2023-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2023-01-01'::date, 'Communication Skills', 8, '2023-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2023-01-01'::date, 'Leadership & Initiative', 8, '2023-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2023-01-01'::date, 'Teamwork & Collaboration', 7, '2023-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2023-01-01'::date, 'Problem Solving', 7, '2023-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2023-01-01'::date, 'Communication Skills', 7, '2023-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2023-01-01'::date, 'Teamwork & Collaboration', 8, '2023-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2023-01-01'::date, 'Problem Solving', 6, '2023-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2023-01-01'::date, 'Customer Orientation', 7, '2023-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2023-01-01'::date, 'Communication Skills', 8, '2023-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2023-01-01'::date, 'Customer Orientation', 9, '2023-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2023-01-01'::date, 'Leadership & Initiative', 8, '2023-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2023-01-01'::date, 'Problem Solving', 7, '2023-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2023-01-01'::date, 'Communication Skills', 7, '2023-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2023-01-01'::date, 'Customer Orientation', 8, '2023-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2023-01-01'::date, 'Problem Solving', 7, '2023-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2023-01-01'::date, 'Teamwork & Collaboration', 7, '2023-12-28'),

    -- 2024
    ('a1a1a1a1-0000-0000-0000-000000000001', '2024-01-01'::date, 'Technical Proficiency', 8, '2024-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2024-01-01'::date, 'Communication Skills', 7, '2024-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2024-01-01'::date, 'Problem Solving', 8, '2024-12-28'),
    ('a1a1a1a1-0000-0000-0000-000000000001', '2024-01-01'::date, 'Leadership & Initiative', 9, '2024-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2024-01-01'::date, 'Technical Proficiency', 7, '2024-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2024-01-01'::date, 'Communication Skills', 6, '2024-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2024-01-01'::date, 'Problem Solving', 7, '2024-12-28'),
    ('b2b2b2b2-0000-0000-0000-000000000001', '2024-01-01'::date, 'Teamwork & Collaboration', 8, '2024-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2024-01-01'::date, 'Technical Proficiency', 9, '2024-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2024-01-01'::date, 'Communication Skills', 7, '2024-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2024-01-01'::date, 'Problem Solving', 8, '2024-12-28'),
    ('c3c3c3c3-0000-0000-0000-000000000001', '2024-01-01'::date, 'Teamwork & Collaboration', 7, '2024-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2024-01-01'::date, 'Communication Skills', 8, '2024-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2024-01-01'::date, 'Leadership & Initiative', 8, '2024-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2024-01-01'::date, 'Teamwork & Collaboration', 8, '2024-12-28'),
    ('d4d4d4d4-0000-0000-0000-000000000001', '2024-01-01'::date, 'Problem Solving', 7, '2024-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2024-01-01'::date, 'Communication Skills', 7, '2024-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2024-01-01'::date, 'Teamwork & Collaboration', 8, '2024-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2024-01-01'::date, 'Problem Solving', 6, '2024-12-28'),
    ('e5e5e5e5-0000-0000-0000-000000000001', '2024-01-01'::date, 'Customer Orientation', 7, '2024-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2024-01-01'::date, 'Communication Skills', 8, '2024-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2024-01-01'::date, 'Customer Orientation', 9, '2024-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2024-01-01'::date, 'Leadership & Initiative', 8, '2024-12-28'),
    ('f6f6f6f6-0000-0000-0000-000000000001', '2024-01-01'::date, 'Problem Solving', 8, '2024-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2024-01-01'::date, 'Communication Skills', 7, '2024-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2024-01-01'::date, 'Customer Orientation', 8, '2024-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2024-01-01'::date, 'Problem Solving', 7, '2024-12-28'),
    ('07070707-0000-0000-0000-000000000001', '2024-01-01'::date, 'Teamwork & Collaboration', 8, '2024-12-28')
) AS r(staff_id, cycle_start_date, competency_name, rating, created_at)
JOIN evaluation_cycle ec ON ec.start_date = r.cycle_start_date
JOIN evaluation ev ON ev.staff_id = r.staff_id::uuid
                  AND ev.evaluation_cycle_id = ec.id
JOIN competency c ON c.name = r.competency_name;