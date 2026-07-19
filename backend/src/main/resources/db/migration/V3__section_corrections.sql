CREATE TABLE section_corrections (
    list_id         UUID NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
    normalized_name VARCHAR(100) NOT NULL,
    section         VARCHAR(40) NOT NULL,
    ts              BIGINT NOT NULL,
    modified_by     UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (list_id, normalized_name)
);
