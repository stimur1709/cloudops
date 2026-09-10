package com.github.stimur1709.cloudops.task.runcommand;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.binding.ResourceCredentialJpaRepository;
import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.ssh.SshEndpointResolver;
import com.github.stimur1709.cloudops.task.TaskType;
import com.github.stimur1709.cloudops.task.capability.TaskCapabilityAssessment;
import com.github.stimur1709.cloudops.task.capability.TaskCapabilityProvider;
import com.github.stimur1709.cloudops.task.capability.TaskCapabilityReason;
import java.util.ArrayList;
import java.util.EnumSet;
import org.springframework.stereotype.Component;

@Component
public class RunCommandCapabilityProvider implements TaskCapabilityProvider {

    private static final EnumSet<MembershipRole> ALLOWED_ROLES = EnumSet.of(MembershipRole.OWNER, MembershipRole.ADMIN);

    private final ResourceCredentialJpaRepository credentialRepository;

    public RunCommandCapabilityProvider(ResourceCredentialJpaRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    @Override
    public TaskType type() {
        return TaskType.RUN_COMMAND;
    }

    @Override
    public TaskCapabilityAssessment assess(ResourceEntity resource, ResourceConfig config) {
        boolean supported = SshEndpointResolver.supports(config);
        var reasons = new ArrayList<TaskCapabilityReason>();
        if (!supported) {
            reasons.add(TaskCapabilityReason.UNSUPPORTED_RESOURCE_TYPE);
        }
        if (resource.status() != ResourceStatus.ACTIVE) {
            reasons.add(TaskCapabilityReason.RESOURCE_INACTIVE);
        }
        if (supported && !SshEndpointResolver.isConfigured(config)) {
            reasons.add(TaskCapabilityReason.SSH_ENDPOINT_NOT_CONFIGURED);
        }
        if (supported && !credentialRepository.existsByResourceIdAndPurpose(resource.id(), CredentialPurpose.SSH)) {
            reasons.add(TaskCapabilityReason.SSH_CREDENTIAL_NOT_CONFIGURED);
        }
        return new TaskCapabilityAssessment(supported, reasons);
    }

    @Override
    public boolean allowed(MembershipRole role) {
        return ALLOWED_ROLES.contains(role);
    }
}
