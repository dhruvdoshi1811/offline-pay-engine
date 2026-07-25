CREATE TABLE relay_logs (
    id UUID PRIMARY KEY,
    packet_id UUID NOT NULL,
    relay_path_id VARCHAR(100) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_relay_logs_packet FOREIGN KEY (packet_id) REFERENCES payment_packets (id)
);

CREATE TABLE settlement_ledger_entries (
    id UUID PRIMARY KEY,
    packet_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    balance_after NUMERIC(19,4) NOT NULL,
    settled_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_packet FOREIGN KEY (packet_id) REFERENCES payment_packets (id)
);
