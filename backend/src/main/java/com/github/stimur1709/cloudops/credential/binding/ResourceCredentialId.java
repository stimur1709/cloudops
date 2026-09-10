package com.github.stimur1709.cloudops.credential.binding;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import java.io.Serializable;

public record ResourceCredentialId(Long resourceId, CredentialPurpose purpose) implements Serializable {}
