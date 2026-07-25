CREATE TABLE payment_packets (
    id UUID PRIMARY KEY,
    sender_device_id UUID NOT NULL,
    receiver_device_id UUID NOT NULL,
    ciphertext TEXT NOT NULL,
    ciphertext_hash VARCHAR(64) NOT NULL,
    encrypted_session_key TEXT NOT NULL,
    nonce TEXT NOT NULL,
    packet_timestamp TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_payment_packets_ciphertext_hash UNIQUE (ciphertext_hash),
    CONSTRAINT fk_payment_packets_sender FOREIGN KEY (sender_device_id) REFERENCES devices (id),
    CONSTRAINT fk_payment_packets_receiver FOREIGN KEY (receiver_device_id) REFERENCES devices (id)
);
