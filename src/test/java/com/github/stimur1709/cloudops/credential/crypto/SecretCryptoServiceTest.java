package com.github.stimur1709.cloudops.credential.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecretCryptoServiceTest {
    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void encryptsAndDecryptsWithRandomNonce() {
        SecretCryptoService service = new SecretCryptoService(new CredentialCryptoProperties(KEY));
        String first = service.encrypt("highly-secret");
        String second = service.encrypt("highly-secret");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("highly-secret");
        assertThat(service.decrypt(second)).isEqualTo("highly-secret");
    }

    @Test
    void rejectsDamagedCiphertextWithoutIncludingSecret() {
        SecretCryptoService service = new SecretCryptoService(new CredentialCryptoProperties(KEY));
        String encrypted = service.encrypt("do-not-disclose");

        assertThatThrownBy(() -> service.decrypt(encrypted.substring(0, encrypted.length() - 2) + "AA"))
                .isInstanceOf(SecretDecryptionException.class)
                .hasMessageNotContaining("do-not-disclose")
                .hasMessageNotContaining(encrypted);
    }

    @Test
    void rejectsCiphertextEncryptedWithAnotherMasterKey() {
        SecretCryptoService first = new SecretCryptoService(new CredentialCryptoProperties(KEY));
        SecretCryptoService second =
                new SecretCryptoService(new CredentialCryptoProperties("YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="));
        assertThatThrownBy(() -> second.decrypt(first.encrypt("sensitive-value")))
                .isInstanceOf(SecretDecryptionException.class)
                .hasMessageNotContaining("sensitive-value");
    }

    @Test
    void requiresValid256BitMasterKey() {
        assertThatThrownBy(() -> new CredentialCryptoProperties(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CredentialCryptoProperties("bm90LTM yLWJ5dGVz"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
