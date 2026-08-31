package com.github.stimur1709.cloudops.credential.api;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;

public record ResourceCredentialResponse(CredentialPurpose purpose, CredentialResponse credential) {
}
