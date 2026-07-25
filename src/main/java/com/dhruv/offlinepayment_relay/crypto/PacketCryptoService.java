package com.dhruv.offlinepayment_relay.crypto;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;

@Service
public class PacketCryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH_BYTES = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedPayload encrypt(PublicKey recipientPublicKey, byte[] plaintext) throws GeneralSecurityException {
        SecretKey sessionKey = generateSessionKey();

        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);

        Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
        aesCipher.init(Cipher.ENCRYPT_MODE, sessionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
        byte[] ciphertext = aesCipher.doFinal(plaintext);

        byte[] encryptedSessionKey = wrapSessionKey(recipientPublicKey, sessionKey);

        return new EncryptedPayload(ciphertext, encryptedSessionKey, nonce);
    }

    public byte[] decrypt(PrivateKey recipientPrivateKey, byte[] ciphertext, byte[] encryptedSessionKey, byte[] nonce)
            throws GeneralSecurityException {
        SecretKey sessionKey = unwrapSessionKey(recipientPrivateKey, encryptedSessionKey);

        Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
        aesCipher.init(Cipher.DECRYPT_MODE, sessionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
        return aesCipher.doFinal(ciphertext);
    }

    private SecretKey generateSessionKey() throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    private byte[] wrapSessionKey(PublicKey publicKey, SecretKey sessionKey) throws GeneralSecurityException {
        Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParameterSpec());
        return rsaCipher.doFinal(sessionKey.getEncoded());
    }

    private SecretKey unwrapSessionKey(PrivateKey privateKey, byte[] encryptedSessionKey) throws GeneralSecurityException {
        Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParameterSpec());
        byte[] rawKey = rsaCipher.doFinal(encryptedSessionKey);
        return new SecretKeySpec(rawKey, "AES");
    }

    private OAEPParameterSpec oaepParameterSpec() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }

    public record EncryptedPayload(byte[] ciphertext, byte[] encryptedSessionKey, byte[] nonce) {
    }
}
