package com.github.stimur1709.cloudops.task.capability;

import com.github.stimur1709.cloudops.common.application.ForbiddenException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.membership.application.OrganizationAuthorization;
import com.github.stimur1709.cloudops.resource.config.ResourceConfig;
import com.github.stimur1709.cloudops.resource.config.ResourceConfigMapper;
import com.github.stimur1709.cloudops.resource.persistence.ResourceEntity;
import com.github.stimur1709.cloudops.resource.persistence.ResourceJpaRepository;
import com.github.stimur1709.cloudops.task.TaskType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCapabilityResolver {

    private final ResourceJpaRepository resourceRepository;
    private final OrganizationAuthorization authorization;
    private final ResourceConfigMapper configMapper;
    private final TaskCapabilityProviderRegistry providerRegistry;

    public TaskCapabilityResolver(
            ResourceJpaRepository resourceRepository,
            OrganizationAuthorization authorization,
            ResourceConfigMapper configMapper,
            TaskCapabilityProviderRegistry providerRegistry) {
        this.resourceRepository = resourceRepository;
        this.authorization = authorization;
        this.configMapper = configMapper;
        this.providerRegistry = providerRegistry;
    }

    @Transactional(readOnly = true)
    public List<TaskCapability> resolve(long resourceId, long currentUserId) {
        ResourceEntity resource = resourceRepository.findById(resourceId).orElseThrow(NotFoundException::new);
        MembershipRole role = authorization.requireMember(resource.organizationId(), currentUserId);
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        return providerRegistry.all().stream()
                .map(provider -> resolve(provider, resource, config, role))
                .toList();
    }

    public void requireAvailable(ResourceEntity resource, TaskType type, long currentUserId) {
        MembershipRole role = authorization.requireMember(resource.organizationId(), currentUserId);
        ResourceConfig config = configMapper.fromJson(resource.type(), resource.config());
        TaskCapability capability = resolve(providerRegistry.get(type), resource, config, role);
        if (!capability.allowed()) {
            throw new ForbiddenException();
        }
        if (!capability.available()) {
            throw capability.reasons().getFirst().conflict();
        }
    }

    private TaskCapability resolve(
            TaskCapabilityProvider provider, ResourceEntity resource, ResourceConfig config, MembershipRole role) {
        TaskCapabilityAssessment assessment = provider.assess(resource, config);
        boolean allowed = provider.allowed(role);
        List<TaskCapabilityReason> reasons = new ArrayList<>(assessment.reasons());
        if (!allowed) {
            reasons.add(TaskCapabilityReason.NOT_AUTHORIZED);
        }
        reasons.sort(Comparator.naturalOrder());
        boolean available = assessment.supported() && assessment.reasons().isEmpty();
        return new TaskCapability(provider.type(), assessment.supported(), available, allowed, reasons);
    }
}
