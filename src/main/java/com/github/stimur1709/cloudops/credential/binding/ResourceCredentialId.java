package com.github.stimur1709.cloudops.credential.binding;

import java.io.Serializable;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;

public record ResourceCredentialId(Long resourceId, CredentialPurpose purpose) implements Serializable {
}
