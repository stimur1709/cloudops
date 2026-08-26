package com.github.stimur1709.cloudops.membership.persistence;

import java.util.List;
import java.util.Optional;

import com.github.stimur1709.cloudops.membership.MembershipRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationMembershipJpaRepository
        extends JpaRepository<OrganizationMembershipEntity, Long> {

    boolean existsByOrganizationId(long organizationId);

    boolean existsByUserId(long userId);

    boolean existsByOrganizationIdAndUserId(long organizationId, long userId);

    Optional<OrganizationMembershipEntity> findByOrganizationIdAndUserId(long organizationId, long userId);

    @Query("""
            select membership.role from OrganizationMembershipEntity membership
            where membership.organizationId = :organizationId and membership.userId = :userId
            """)
    Optional<MembershipRole> findRole(
            @Param("organizationId") long organizationId,
            @Param("userId") long userId
    );

    boolean existsByUserIdAndRoleIn(long userId, List<MembershipRole> roles);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select membership from OrganizationMembershipEntity membership
            where membership.organizationId = :organizationId
            order by membership.id
            """)
    List<OrganizationMembershipEntity> lockAllByOrganizationId(@Param("organizationId") long organizationId);
}
