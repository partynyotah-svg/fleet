ALTER TABLE vehicle ADD COLUMN tracker_phone VARCHAR(24);
ALTER TABLE vehicle ADD COLUMN tracker_protocol VARCHAR(16) NOT NULL DEFAULT 'DISABLED'
    CHECK (tracker_protocol IN ('DISABLED', 'TK103'));