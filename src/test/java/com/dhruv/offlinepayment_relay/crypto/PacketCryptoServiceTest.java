package com.dhruv.offlinepayment_relay.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketCryptoServiceTest {

    private final PacketCryptoService cryptoService = new PacketCryptoService();

    @Test
    void encryptThenDecryptReturnsOriginalPlaintext() throws Exception {
        KeyPair keyPair = generateKeyPair();
        byte[] plaintext = "{\"amount\":42.50}".getBytes(StandardCharsets.UTF_8);

        PacketCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(keyPair.getPublic(), plaintext);
        byte[] decrypted = cryptoService.decrypt(
                keyPair.getPrivate(), encrypted.ciphertext(), encrypted.encryptedSessionKey(), encrypted.nonce());

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void decryptingWithWrongPrivateKeyFails() throws Exception {
        KeyPair recipientKeyPair = generateKeyPair();
        KeyPair wrongKeyPair = generateKeyPair();
        byte[] plaintext = "{\"amount\":10}".getBytes(StandardCharsets.UTF_8);

        PacketCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(recipientKeyPair.getPublic(), plaintext);

        assertThrows(Exception.class, () -> cryptoService.decrypt(
                wrongKeyPair.getPrivate(), encrypted.ciphertext(), encrypted.encryptedSessionKey(), encrypted.nonce()));
    }

    @Test
    void tamperedCiphertextFailsAuthenticationTag() throws Exception {
        KeyPair keyPair = generateKeyPair();
        byte[] plaintext = "{\"amount\":10}".getBytes(StandardCharsets.UTF_8);

        PacketCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(keyPair.getPublic(), plaintext);
        byte[] tampered = Arrays.copyOf(encrypted.ciphertext(), encrypted.ciphertext().length);
        tampered[0] ^= 0x01;

        assertThrows(Exception.class, () -> cryptoService.decrypt(
                keyPair.getPrivate(), tampered, encrypted.encryptedSessionKey(), encrypted.nonce()));
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
