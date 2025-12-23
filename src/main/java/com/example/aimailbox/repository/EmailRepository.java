package com.example.aimailbox.repository;

import com.example.aimailbox.model.Email;
import com.example.aimailbox.model.EmailStatus;
import com.example.aimailbox.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {
    
    Optional<Email> findByUserAndThreadId(User user, String threadId);
    
    List<Email> findByUserAndStatus(User user, EmailStatus status);

    List<Email> findByUserAndStatus(User user, EmailStatus status, Sort sort);
    
    List<Email> findByUserOrderByReceivedAtDesc(User user);

    List<Email> findByUser(User user, Sort sort);
    
    @Query("SELECT e FROM Email e WHERE e.user = :user AND e.status = 'SNOOZED' AND e.snoozedUntil <= :now")
    List<Email> findSnoozedEmailsToRestore(@Param("user") User user, @Param("now") Instant now);
    
    @Query("SELECT e FROM Email e WHERE e.status = 'SNOOZED' AND e.snoozedUntil <= :now")
    List<Email> findAllSnoozedEmailsToRestore(@Param("now") Instant now);
    
    long countByUserAndStatus(User user, EmailStatus status);

    List<Email> findByUserAndLabelIdsContaining(User user, String labelId, Sort sort);

    // Filter by Unread Status
    List<Email> findByUserAndLabelIdsContainingAndIsRead(User user, String labelId, Boolean isRead, Sort sort);
    
    List<Email> findByUserAndStatusAndIsRead(User user, EmailStatus status, Boolean isRead, Sort sort);
    
    List<Email> findByUserAndIsRead(User user, Boolean isRead, Sort sort);

    List<Email> findByUserAndHasAttachments(User user, Boolean hasAttachments, Sort sort);
    
    List<Email> findByUserAndIsReadAndHasAttachments(User user, Boolean isRead, Boolean hasAttachments, Sort sort);
    
    List<Email> findByUserAndLabelIdsContainingAndHasAttachments(User user, String labelId, Boolean hasAttachments, Sort sort);

    List<Email> findByUserAndLabelIdsContainingAndIsReadAndHasAttachments(User user, String labelId, Boolean isRead, Boolean hasAttachments, Sort sort);
    
    List<Email> findByUserAndStatusAndHasAttachments(User user, EmailStatus status, Boolean hasAttachments, Sort sort);

    List<Email> findByUserAndStatusAndIsReadAndHasAttachments(User user, EmailStatus status, Boolean isRead, Boolean hasAttachments, Sort sort);

    Optional<Email> findFirstByUserOrderByReceivedAtDesc(User user);

    @Query(value = """
        SELECT * FROM emails e
        WHERE e.user_id = :userId
        AND (e.embedding <-> CAST(:queryVector AS VECTOR)) < :threshold  -- Thêm dòng này
        ORDER BY e.embedding <-> CAST(:queryVector AS VECTOR)
        LIMIT 20
        """, nativeQuery = true)
    List<Email> searchBySemantic(@Param("userId") Long userId,
                                 @Param("queryVector") float[] queryVector,
                                 @Param("threshold") double threshold);
}
