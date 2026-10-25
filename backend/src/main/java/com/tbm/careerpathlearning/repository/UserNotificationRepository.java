package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long>, UserNotificationInsert {

    List<UserNotification> findTop30ByRecipientIdOrderByCreatedAtDescIdDesc(UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    boolean existsByDedupeKey(String dedupeKey);

    @Modifying
    @Query("update UserNotification n set n.readAt = :at where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("at") OffsetDateTime at);
}
