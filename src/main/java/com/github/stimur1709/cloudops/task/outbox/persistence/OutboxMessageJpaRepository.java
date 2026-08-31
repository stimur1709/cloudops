package com.github.stimur1709.cloudops.task.outbox.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageJpaRepository extends JpaRepository<OutboxMessageEntity, UUID> {

    @Query("""
            SELECT message.id
              FROM OutboxMessageEntity message
             WHERE message.publishedAt IS NULL
             ORDER BY message.createdAt, message.id
            """)
    java.util.List<UUID> findUnpublishedIds(Pageable pageable);

    @Query(value = """
            SELECT *
              FROM outbox_messages
             WHERE id = :id AND published_at IS NULL
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxMessageEntity> lockUnpublished(@Param("id") UUID id);
}
