-- Course categories (FR-19): Hibernate creates course_category and course_category_link. This adds the defaults
-- (decision C, 29 Oct 2026), turns the department names already typed on owned courses into custom categories, and
-- gives every course a primary category: its department's, or General. Each step skips what already exists.
INSERT INTO course_category (name, color, icon, code, retired, created_at, updated_at)
SELECT v.name, v.color, v.icon, v.code, false, now(), now()
FROM (VALUES ('General', '#475569', 'appstore', 'GENERAL'),
             ('Sales', '#B45309', 'shop', 'SALES'),
             ('Repairs & Service', '#1D4ED8', 'tool', 'REPAIRS_SERVICE'),
             ('Product Knowledge', '#7C3AED', 'gift', 'PRODUCT_KNOWLEDGE'),
             ('Safety & Compliance', '#DC2626', 'safety', 'SAFETY_COMPLIANCE'),
             ('Leadership', '#0F766E', 'crown', 'LEADERSHIP'),
             ('Digital Skills', '#0891B2', 'laptop', 'DIGITAL_SKILLS')) AS v(name, color, icon, code)
WHERE NOT EXISTS (SELECT 1 FROM course_category c WHERE c.code = v.code OR lower(c.name) = lower(v.name));

-- Department names on owned courses (ownerless evaluation documents are left out), each once, made by whoever first
-- typed it, so they can still change it; coloured from the palette colours no default uses.
INSERT INTO course_category (name, color, icon, retired, created_by, created_at, updated_at)
SELECT t.name,
       (ARRAY['#9333EA', '#EA580C', '#059669'])[1 + ((row_number() OVER (ORDER BY lower(t.name)) - 1) % 3)::int],
       'tag', false, t.created_by, now(), now()
FROM (SELECT DISTINCT ON (lower(left(btrim(department_tag), 60))) left(btrim(department_tag), 60) AS name,
             uploaded_by AS created_by
      FROM sop_document
      WHERE NOT is_deleted AND uploaded_by IS NOT NULL AND department_tag IS NOT NULL
        AND btrim(department_tag) NOT IN ('', '-')
      ORDER BY lower(left(btrim(department_tag), 60)), id) t
WHERE NOT EXISTS (SELECT 1 FROM course_category c WHERE lower(c.name) = lower(t.name));

-- Each course, by the key all its versions share, gets the category of its newest version's department, else General.
INSERT INTO course_category_link (course_key, category_id, is_primary)
SELECT k.course_key, coalesce(c.category_id, g.category_id), true
FROM (SELECT DISTINCT ON (coalesce(course_id, id)) coalesce(course_id, id) AS course_key,
             CASE WHEN uploaded_by IS NULL THEN NULL ELSE left(btrim(department_tag), 60) END AS tag
      FROM sop_document
      WHERE NOT is_deleted
      ORDER BY coalesce(course_id, id), version_no DESC, id DESC) k
LEFT JOIN course_category c ON k.tag IS NOT NULL AND lower(c.name) = lower(k.tag)
CROSS JOIN (SELECT category_id FROM course_category WHERE code = 'GENERAL') g
WHERE NOT EXISTS (SELECT 1 FROM course_category_link l WHERE l.course_key = k.course_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_course_category_name ON course_category (lower(name));
CREATE UNIQUE INDEX IF NOT EXISTS uq_course_category_code ON course_category (code) WHERE code IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_course_category_link ON course_category_link (course_key, category_id);
CREATE INDEX IF NOT EXISTS idx_course_category_link_category ON course_category_link (category_id);
