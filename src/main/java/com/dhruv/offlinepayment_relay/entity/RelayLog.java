package com.dhruv.offlinepayment_relay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "relay_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class RelayLog {

    @Id
    private UUID id;

    @Column(name = "packet_id", nullable = false)
    private UUID packetId;

    @Column(name = "relay_path_id", nullable = false)
    private String relayPathId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
