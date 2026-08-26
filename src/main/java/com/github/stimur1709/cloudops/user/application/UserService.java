package com.github.stimur1709.cloudops.user.application;

import java.time.Clock;

import com.github.stimur1709.cloudops.common.application.ConflictException;
import com.github.stimur1709.cloudops.common.application.ForbiddenException;
import com.github.stimur1709.cloudops.common.application.NotFoundException;
import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipJpaRepository;
import com.github.stimur1709.cloudops.membership.MembershipRole;
import com.github.stimur1709.cloudops.user.persistence.UserEntity;
import com.github.stimur1709.cloudops.user.persistence.UserJpaRepository;
import com.github.stimur1709.cloudops.user.persistence.UserSearchDefinition;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserJpaRepository userRepository;
    private final OrganizationMembershipJpaRepository membershipRepository;
    private final JpaSearchService searchService;
    private final Clock clock;

    public UserService(
            UserJpaRepository userRepository,
            OrganizationMembershipJpaRepository membershipRepository,
            JpaSearchService searchService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.searchService = searchService;
        this.clock = clock;
    }

    @Transactional
    public UserEntity register(String email, String displayName, String passwordHash) {
        return save(UserEntity.create(email, displayName, passwordHash, clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserEntity get(long id) {
        return userRepository.findById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional(readOnly = true)
    public UserEntity getOwn(long id, long currentUserId) {
        requireSelf(id, currentUserId);
        return get(id);
    }

    @Transactional(readOnly = true)
    public SearchResult<UserEntity> search(SearchQuery search, long currentUserId) {
        if (!membershipRepository.existsByUserIdAndRoleIn(
                currentUserId, java.util.List.of(MembershipRole.OWNER, MembershipRole.ADMIN)
        )) {
            throw new ForbiddenException();
        }
        return searchService.search(search, UserSearchDefinition.DEFINITION);
    }

    @Transactional
    public UserEntity update(long id, String email, String displayName, long currentUserId) {
        requireSelf(id, currentUserId);
        UserEntity user = userRepository.findById(id).orElseThrow(NotFoundException::new);
        user.update(email, displayName, clock.instant());
        return save(user);
    }

    @Transactional
    public void delete(long id, long currentUserId) {
        requireSelf(id, currentUserId);
        UserEntity user = userRepository.findById(id).orElseThrow(NotFoundException::new);
        if (membershipRepository.existsByUserId(id)) {
            throw userInUse();
        }
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw userInUse();
        }
    }

    private UserEntity save(UserEntity user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    "USER_EMAIL_CONFLICT",
                    "Email is already used by another user"
            );
        }
    }

    private ConflictException userInUse() {
        return new ConflictException(
                "USER_IN_USE",
                "User cannot be deleted while they belong to an organization"
        );
    }

    private void requireSelf(long id, long currentUserId) {
        if (id != currentUserId) {
            throw new ForbiddenException();
        }
    }
}
