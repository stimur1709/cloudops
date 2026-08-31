package com.github.stimur1709.cloudops.credential.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.stimur1709.cloudops.credential.CredentialType;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UsernamePasswordCredentialRequest.class, name = "USERNAME_PASSWORD"),
        @JsonSubTypes.Type(value = SshPrivateKeyCredentialRequest.class, name = "SSH_PRIVATE_KEY")
})
public sealed interface CredentialRequest
        permits UsernamePasswordCredentialRequest, SshPrivateKeyCredentialRequest {

    String name();

    CredentialType type();

    String username();

    String secret();
}
