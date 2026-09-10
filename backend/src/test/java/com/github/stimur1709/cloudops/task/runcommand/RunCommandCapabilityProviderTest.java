package com.github.stimur1709.cloudops.task.runcommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.stimur1709.cloudops.credential.CredentialPurpose;
import com.github.stimur1709.cloudops.credential.binding.ResourceCredentialJpaRepository;
import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.resource.ResourceStatus;
import com.github.stimur1709.cloudops.resource.config.NetworkDeviceResourceConfig;
import com.github.stimur1709.cloudops.resource.config.OtherResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ServerResourceConfig;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.task.capability.TaskCapabilityReason;
import org.junit.jupiter.api.Test;

class RunCommandCapabilityProviderTest {

    private final ResourceCredentialJpaRepository credentials = mock(ResourceCredentialJpaRepository.class);
    private final RunCommandCapabilityProvider provider = new RunCommandCapabilityProvider(credentials);

    @Test
    void configuredActiveServerIsSupportedAndAvailable() {
        ResourceEntity resource = resource(ResourceStatus.ACTIVE);
        when(credentials.existsByResourceIdAndPurpose(9, CredentialPurpose.SSH)).thenReturn(true);

        var assessment = provider.assess(resource, new ServerResourceConfig("server.internal", null));

        assertThat(assessment.supported()).isTrue();
        assertThat(assessment.reasons()).isEmpty();
        assertThat(provider.allowed(MembershipRole.OWNER)).isTrue();
        assertThat(provider.allowed(MembershipRole.ADMIN)).isTrue();
        assertThat(provider.allowed(MembershipRole.MEMBER)).isFalse();
    }

    @Test
    void configuredNetworkDeviceIsSupportedAndAvailable() {
        ResourceEntity resource = resource(ResourceStatus.ACTIVE);
        when(credentials.existsByResourceIdAndPurpose(9, CredentialPurpose.SSH)).thenReturn(true);

        var assessment = provider.assess(resource, new NetworkDeviceResourceConfig("switch.internal", null));

        assertThat(assessment.supported()).isTrue();
        assertThat(assessment.reasons()).isEmpty();
    }

    @Test
    void reportsAllApplicableReasonsInStableContractOrder() {
        ResourceEntity resource = resource(ResourceStatus.INACTIVE);

        var assessment = provider.assess(resource, new ServerResourceConfig(" ", null));

        assertThat(assessment.supported()).isTrue();
        assertThat(assessment.reasons())
                .containsExactly(
                        TaskCapabilityReason.RESOURCE_INACTIVE,
                        TaskCapabilityReason.SSH_ENDPOINT_NOT_CONFIGURED,
                        TaskCapabilityReason.SSH_CREDENTIAL_NOT_CONFIGURED);
    }

    @Test
    void unsupportedResourceDoesNotReportSshPrerequisites() {
        var assessment = provider.assess(resource(ResourceStatus.ACTIVE), new OtherResourceConfig());

        assertThat(assessment.supported()).isFalse();
        assertThat(assessment.reasons()).containsExactly(TaskCapabilityReason.UNSUPPORTED_RESOURCE_TYPE);
    }

    private ResourceEntity resource(ResourceStatus status) {
        ResourceEntity resource = mock(ResourceEntity.class);
        when(resource.id()).thenReturn(9L);
        when(resource.status()).thenReturn(status);
        return resource;
    }
}
