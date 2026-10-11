-- A photo can contain several independent products. Keep the legacy single
-- selection columns as historical data; new confirmations live in this array.
ALTER TABLE bozor_poc.scan_records
    ADD COLUMN reviewed_products jsonb;

ALTER TABLE bozor_poc.scan_records
    ADD CONSTRAINT scan_records_reviewed_products_array
    CHECK (reviewed_products IS NULL OR jsonb_typeof(reviewed_products) = 'array');
