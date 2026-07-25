package com.dhruv.offlinepayment_relay.repository;

import com.dhruv.offlinepayment_relay.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
}
