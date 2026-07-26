ALTER TABLE devices ADD COLUMN owner_user_id UUID NOT NULL;
ALTER TABLE devices ADD CONSTRAINT fk_devices_owner FOREIGN KEY (owner_user_id) REFERENCES users (id);
