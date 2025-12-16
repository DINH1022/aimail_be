package com.example.aimailbox.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "emails", indexes = {
    @Index(name = "idx_user_thread", columnList = "user_id,thread_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_snoozed_until", columnList = "snoozed_until")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Email {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String threadId;

    @Column(name = "from_address")
    private String from;

    @Column(name = "to_address", length = 1000)
    private String to;

    @Column(length = 500)
    private String subject;

    @Column(length = 1000)
    private String snippet;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmailStatus status = EmailStatus.INBOX;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    @Column(name = "previous_status")
    @Enumerated(EnumType.STRING)
    private EmailStatus previousStatus;

    @Column(columnDefinition = "TEXT")
    private String labelIds; 

    @Column(name = "is_read")
    private Boolean isRead; 

    @Column(name = "is_starred")
    private Boolean isStarred; 
    
    @Column(name = "has_attachments")
    private Boolean hasAttachments;

    @Column(name = "received_at")
    private Instant receivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
