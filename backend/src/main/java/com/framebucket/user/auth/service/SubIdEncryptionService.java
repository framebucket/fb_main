package com.framebucket.user.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SubIdEncryptionService {
    private static final String CIPHERTEXT_VERSION = "v1:";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SubIdEncryptionService(@Value("${app.sub-id.encryption-key}") String keyMaterial) {
        if (keyMaterial == null || keyMaterial.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("APP_SUB_ID_ENCRYPTION_KEY must contain at least 32 UTF-8 bytes.");
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not initialize sub ID encryption.", exception);
        }
    }

    public String encrypt(String value) {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encryptedValue = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(nonce.length + encryptedValue.length);
            payload.put(nonce).put(encryptedValue);
            return CIPHERTEXT_VERSION + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt the studio sub ID.", exception);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || !encryptedValue.startsWith(CIPHERTEXT_VERSION)) {
            throw new IllegalStateException("The studio sub ID is missing or has an unsupported format.");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encryptedValue.substring(CIPHERTEXT_VERSION.length()));
            if (payload.length <= NONCE_LENGTH + TAG_LENGTH_BITS / 8) {
                throw new IllegalStateException("The encrypted studio sub ID is incomplete.");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] nonce = new byte[NONCE_LENGTH];
            buffer.get(nonce);
            byte[] encryptedBytes = new byte[buffer.remaining()];
            buffer.get(encryptedBytes);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not decrypt the studio sub ID. Check APP_SUB_ID_ENCRYPTION_KEY.", exception);
        }
    }
}
