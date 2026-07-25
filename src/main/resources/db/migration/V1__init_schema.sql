CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE devices (
    id UUID PRIMARY KEY,
    owner_name VARCHAR(255) NOT NULL,
    public_key TEXT NOT NULL,
    registered_at TIMESTAMP NOT NULL
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL,
    balance NUMERIC(19,4) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_wallets_device_id UNIQUE (device_id),
    CONSTRAINT fk_wallets_device FOREIGN KEY (device_id) REFERENCES devices (id)
);
