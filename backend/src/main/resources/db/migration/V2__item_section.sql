ALTER TABLE shopping_items
  ADD COLUMN section_value VARCHAR(40) NOT NULL DEFAULT 'SONSTIGES',
  ADD COLUMN section_ts BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN section_modified_by UUID;
UPDATE shopping_items SET section_modified_by = name_modified_by;
ALTER TABLE shopping_items
  ALTER COLUMN section_modified_by SET NOT NULL,
  ADD CONSTRAINT fk_items_section_modified_by
    FOREIGN KEY (section_modified_by) REFERENCES users(id);
