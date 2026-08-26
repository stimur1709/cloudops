package com.github.stimur1709.cloudops.user.application;

import java.time.Clock;
import java.time.Instant;

import com.github.stimur1709.cloudops.common.persistence.search.JpaSearchService;
import com.github.stimur1709.cloudops.common.search.SearchQuery;
import com.github.stimur1709.cloudops.common.search.SearchResult;
import com.github.stimur1709.cloudops.membership.persistence.OrganizationMembershipJpaRepository;
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
    public UserEntity create(String email, String displayName) {
        return save(UserEntity.create(email, displayName, clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserEntity get(long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public SearchResult<UserEntity> search(SearchQuery search) {
        return searchService.search(search, UserSearchDefinition.DEFINITION);
    }

    @Transactional
    public UserEntity update(long id, String email, String displayName) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        Instant now = clock.instant();
        Instant updatedAt = now.isAfter(user.updatedAt()) ? now : user.updatedAt().plusNanos(1_000);
        user.update(email, displayName, updatedAt);
        return save(user);
    }

    @Transactional
    public void delete(long id) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (membershipRepository.existsByUserId(id)) {
            throw new UserInUseException();
        }
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new UserInUseException();
        }
    }

    private UserEntity save(UserEntity user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new UserEmailConflictException();
        }
    }
}
