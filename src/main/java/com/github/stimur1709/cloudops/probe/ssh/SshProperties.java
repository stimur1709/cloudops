package com.github.stimur1709.cloudops.probe.ssh;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloudops.ssh")
public record SshProperties(
        @NotNull SshHostKeyVerification hostKeyVerification,
        @NotNull Path knownHostsPath) {}
