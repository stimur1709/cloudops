package com.github.stimur1709.cloudops.auth.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT token FROM RefreshTokenEntity token WHERE token.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(String tokenHash);
}
