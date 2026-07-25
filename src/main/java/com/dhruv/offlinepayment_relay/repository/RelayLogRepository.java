package com.dhruv.offlinepayment_relay.repository;

import com.dhruv.offlinepayment_relay.entity.RelayLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RelayLogRepository extends JpaRepository<RelayLog, UUID> {

    @Query("SELECT r.packetId FROM RelayLog r GROUP BY r.packetId HAVING COUNT(r) > 1")
    List<UUID> findPacketIdsWithMultipleDeliveries();

    List<RelayLog> findByPacketId(UUID packetId);
}
