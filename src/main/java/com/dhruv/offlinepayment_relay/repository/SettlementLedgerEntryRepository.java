package com.dhruv.offlinepayment_relay.repository;

import com.dhruv.offlinepayment_relay.entity.SettlementLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementLedgerEntryRepository extends JpaRepository<SettlementLedgerEntry, UUID> {

    List<SettlementLedgerEntry> findByPacketIdInOrderBySettledAtAsc(List<UUID> packetIds);
}
