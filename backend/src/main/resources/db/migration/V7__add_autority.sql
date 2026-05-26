-- add new authority
INSERT INTO authority (name, description_key, label_key)
VALUES
    ('CAN_MANAGE_CAREER_PATHWAY', 'auth.can.manage.career.pathway.desc', 'auth.can.manage.career.pathway.label'),
    ('CAN_VIEW_CAREER_PATHWAY', 'auth.can.view.career.pathway.desc', 'auth.can.view.career.pathway.label'),
    ('CAN_MANAGE_ORG_CHART', 'auth.can.manage.org.chart.desc', 'auth.can.manage.org.chart.label');

-- DROP TABLE
DROP TABLE IF EXISTS parent_child_node;

-- CREATE TABLE
CREATE TABLE parent_child_node
(
    id            BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT NOT NULL,
    child_id      BIGINT NOT NULL,
    relation_type VARCHAR(50),
    created_by    UUID,
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_by    UUID,
    updated_at    TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT fk_pcn_updated_by FOREIGN KEY (updated_by)
        REFERENCES staff (id) ON DELETE SET NULL,

    CONSTRAINT fk_rcp_created_by FOREIGN KEY (created_by)
        REFERENCES staff (id) ON DELETE SET NULL
);



