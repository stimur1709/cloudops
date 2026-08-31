package com.github.stimur1709.cloudops.credential.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Component;

@Component
public class SecretCryptoService {
    private static final byte VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final CredentialCryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCryptoService(CredentialCryptoProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, properties.key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(1 + IV_LENGTH + ciphertext.length)
                            .put(VERSION)
                            .put(iv)
                            .put(ciphertext)
                            .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Credential encryption failed", exception);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] payload = Base64.getDecoder().decode(encrypted);
            if (payload.length <= 1 + IV_LENGTH || payload[0] != VERSION) {
                throw new GeneralSecurityException("Unsupported credential ciphertext");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[payload.length - 1 - IV_LENGTH];
            System.arraycopy(payload, 1, iv, 0, IV_LENGTH);
            System.arraycopy(payload, 1 + IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, properties.key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new SecretDecryptionException(exception);
        }
    }
}
