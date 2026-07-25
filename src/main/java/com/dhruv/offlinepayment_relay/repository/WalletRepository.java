package com.dhruv.offlinepayment_relay.repository;

import com.dhruv.offlinepayment_relay.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByDeviceId(UUID deviceId);
}
