package com.github.stimur1709.cloudops.credential.crypto;

public final class SecretDecryptionException extends RuntimeException {
    public SecretDecryptionException(Throwable cause) {
        super("Stored credential secret cannot be decrypted", cause);
    }
}
