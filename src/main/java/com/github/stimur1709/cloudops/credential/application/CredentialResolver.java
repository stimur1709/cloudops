package com.github.stimur1709.cloudops.credential.application;

import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.binding.ResourceCredentialEntity;
import com.github.stimur1709.cloudops.credential.binding.ResourceCredentialJpaRepository;
import com.github.stimur1709.cloudops.credential.crypto.SecretCryptoService;
import com.github.stimur1709.cloudops.credential.persistence.CredentialEntity;
import com.github.stimur1709.cloudops.credential.persistence.CredentialJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialResolver {
    private final ResourceCredentialJpaRepository bindingRepository;
    private final CredentialJpaRepository credentialRepository;
    private final SecretCryptoService cryptoService;

    public CredentialResolver(ResourceCredentialJpaRepository bindingRepository,
            CredentialJpaRepository credentialRepository, SecretCryptoService cryptoService) {
        this.bindingRepository = bindingRepository;
        this.credentialRepository = credentialRepository;
        this.cryptoService = cryptoService;
    }

    @Transactional(readOnly = true)
    public ResolvedCredential resolve(long resourceId, CredentialPurpose purpose) {
        ResourceCredentialEntity binding = bindingRepository.findByResourceIdAndPurpose(resourceId, purpose)
                .orElseThrow(NotFoundException::new);
        CredentialEntity credential = credentialRepository.findById(binding.credentialId())
                .orElseThrow(NotFoundException::new);
        String secret = cryptoService.decrypt(credential.secretEncrypted());
        return switch (credential.type()) {
            case USERNAME_PASSWORD -> new ResolvedUsernamePassword(credential.username(), secret);
            case SSH_PRIVATE_KEY -> new ResolvedSshPrivateKey(credential.username(), secret);
        };
    }
}
