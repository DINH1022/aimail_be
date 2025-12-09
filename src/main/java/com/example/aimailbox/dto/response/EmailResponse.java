package com.example.aimailbox.dto.response;

import com.example.aimailbox.model.EmailStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponse {
    
    private Long id;
    private String threadId;
    private String from;
    private String to;
    private String subject;
    private String snippet;
    private String body;
    private String summary;
    private EmailStatus status;
    private Instant snoozedUntil;
    private Boolean isRead;
    private Boolean isStarred;
    private Instant receivedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
