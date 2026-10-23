-- Material review history (FR-07-14): every module's history starts with how it was made. Modules made before the
-- history was kept get that first entry from what is already stored. Their earlier approvals and rejections weren't
-- recorded, so the entry says so rather than guessing them.
CREATE INDEX IF NOT EXISTS idx_sop_material_review_event_module ON sop_material_review_event (sop_module_id);

-- A module copied into a draft: its quiz history already records who copied it, and when.
INSERT INTO sop_material_review_event (sop_module_id, action, detail, actor_id, created_at)
SELECT q.sop_module_id, 'COPIED', q.detail, q.actor_id, q.created_at
FROM sop_quiz_review_event q
WHERE q.action = 'COPIED'
  AND NOT EXISTS (SELECT 1 FROM sop_material_review_event e WHERE e.sop_module_id = q.sop_module_id);

-- Any other module was generated, by the AI or, when it was unavailable, from the SOP text.
INSERT INTO sop_material_review_event (sop_module_id, action, detail, actor_id, created_at)
SELECT m.id, 'GENERATED',
       CASE WHEN m.content LIKE '%"source":"offline"%'
            THEN 'Built from the SOP text because the AI was unavailable. Approvals and rejections made before review history was kept aren''t listed here.'
            ELSE 'Written by the AI from the uploaded SOP. Approvals and rejections made before review history was kept aren''t listed here.'
       END,
       NULL, m.created_at
FROM sop_module m
WHERE NOT EXISTS (SELECT 1 FROM sop_material_review_event e WHERE e.sop_module_id = m.id);

-- Rejecting a module's material regenerates it together with its quiz, and the quiz history kept each time.
INSERT INTO sop_material_review_event (sop_module_id, action, detail, actor_id, created_at)
SELECT q.sop_module_id, 'REGENERATED', 'Rewritten by the AI after the material was rejected, with a new quiz to match.',
       NULL, q.created_at
FROM sop_quiz_review_event q
WHERE q.action = 'REGENERATED' AND q.detail = 'Regenerated together with the rejected module material.';
