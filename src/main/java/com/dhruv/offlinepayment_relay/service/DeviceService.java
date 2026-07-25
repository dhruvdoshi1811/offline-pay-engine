package com.dhruv.offlinepayment_relay.service;

import com.dhruv.offlinepayment_relay.dto.DeviceRequest;
import com.dhruv.offlinepayment_relay.dto.DeviceResponse;
import com.dhruv.offlinepayment_relay.entity.Device;
import com.dhruv.offlinepayment_relay.entity.Wallet;
import com.dhruv.offlinepayment_relay.exception.InvalidRequestException;
import com.dhruv.offlinepayment_relay.exception.ResourceNotFoundException;
import com.dhruv.offlinepayment_relay.repository.DeviceRepository;
import com.dhruv.offlinepayment_relay.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final WalletRepository walletRepository;

    public DeviceService(DeviceRepository deviceRepository, WalletRepository walletRepository) {
        this.deviceRepository = deviceRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional
    public DeviceResponse register(DeviceRequest request) {
        validatePublicKey(request.publicKey());

        Device device = Device.builder()
                .id(UUID.randomUUID())
                .ownerName(request.ownerName())
                .publicKey(request.publicKey())
                .registeredAt(Instant.now())
                .build();
        deviceRepository.save(device);

        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .deviceId(device.getId())
                .balance(BigDecimal.ZERO)
                .build();
        walletRepository.save(wallet);

        return toResponse(device, wallet.getId());
    }

    public DeviceResponse getById(UUID id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("device not found: " + id));
        Wallet wallet = walletRepository.findByDeviceId(id)
                .orElseThrow(() -> new ResourceNotFoundException("wallet not found for device: " + id));
        return toResponse(device, wallet.getId());
    }

    private void validatePublicKey(String publicKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(publicKey);
            KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception ex) {
            throw new InvalidRequestException("publicKey must be a Base64-encoded X.509 RSA public key");
        }
    }

    private DeviceResponse toResponse(Device device, UUID walletId) {
        return new DeviceResponse(
                device.getId(),
                device.getOwnerName(),
                device.getPublicKey(),
                device.getRegisteredAt(),
                walletId
        );
    }
}
