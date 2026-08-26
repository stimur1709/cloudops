package com.github.stimur1709.cloudops.membership.persistence;

import java.time.Instant;

import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.organization.persistence.OrganizationEntity;
import com.github.stimur1709.cloudops.user.persistence.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "organization_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "organization_memberships_organization_user_key",
                columnNames = {"organization_id", "user_id"}
        )
)
public class OrganizationMembershipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Column(name = "organization_id", nullable = false, insertable = false, updatable = false)
    private Long organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MembershipRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationMembershipEntity() {
    }

    private OrganizationMembershipEntity(
            OrganizationEntity organization,
            UserEntity user,
            MembershipRole role,
            Instant createdAt
    ) {
        this.organization = organization;
        this.organizationId = organization.id();
        this.user = user;
        this.userId = user.id();
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static OrganizationMembershipEntity create(
            OrganizationEntity organization,
            UserEntity user,
            MembershipRole role,
            Instant createdAt
    ) {
        return new OrganizationMembershipEntity(organization, user, role, createdAt);
    }

    public void changeRole(MembershipRole role, Instant updatedAt) {
        this.role = role;
        this.updatedAt = updatedAt;
    }

    public Long id() { return id; }
    public Long organizationId() { return organizationId; }
    public Long userId() { return userId; }
    public MembershipRole role() { return role; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
