package com.example.aimailbox.service;

import com.example.aimailbox.dto.request.SnoozeEmailRequest;
import com.example.aimailbox.dto.request.UpdateEmailStatusRequest;
import com.example.aimailbox.dto.response.EmailResponse;
import com.example.aimailbox.dto.response.ThreadDetailResponse; // Add missing import
import com.example.aimailbox.model.Email;
import com.example.aimailbox.model.EmailStatus;
import com.example.aimailbox.model.User;
import com.example.aimailbox.repository.EmailRepository;
import com.example.aimailbox.repository.UserRepository;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final ProxyMailService proxyMailService;
    private final EmbeddingService embeddingService;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new RuntimeException("User not authenticated");
        }
        Object principal = authentication.getPrincipal();

        if (principal instanceof User) {
            return (User) principal;
        } else {
            String email = authentication.getName();
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        }
    }

    /**
     * Get all emails for current user
     */
    private Sort getSort(String sortOption) {
        if (sortOption == null) {
            return Sort.by(Sort.Direction.DESC, "receivedAt");
        }

        switch (sortOption.toLowerCase()) {
            case "oldest":
                return Sort.by(Sort.Direction.ASC, "receivedAt");
            case "sender":
                return Sort.by(Sort.Direction.ASC, "from");
            case "newest":
            default:
                return Sort.by(Sort.Direction.DESC, "receivedAt");
        }
    }

    /**
     * Get all emails for current user
     */
    /**
     * Get all emails for current user
     */
    public List<EmailResponse> getAllEmails(String sortOption, Boolean unreadOnly, Boolean hasAttachments) {
        User user = getCurrentUser();
        List<Email> emails;
        
        boolean isUnread = Boolean.TRUE.equals(unreadOnly);
        boolean isAttachments = Boolean.TRUE.equals(hasAttachments);

        if (isUnread && isAttachments) {
            emails = emailRepository.findByUserAndIsReadAndHasAttachments(user, false, true, getSort(sortOption));
        } else if (isUnread) {
            emails = emailRepository.findByUserAndIsRead(user, false, getSort(sortOption));
        } else if (isAttachments) {
            emails = emailRepository.findByUserAndHasAttachments(user, true, getSort(sortOption));
        } else {
            emails = emailRepository.findByUser(user, getSort(sortOption));
        }
        
        return emails.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get emails by status
     */
    /**
     * Get emails by status
     */
    public List<EmailResponse> getEmailsByStatus(EmailStatus status, String sortOption, Boolean unreadOnly, Boolean hasAttachments) {
        User user = getCurrentUser();
        List<Email> emails;
        
        boolean isUnread = Boolean.TRUE.equals(unreadOnly);
        boolean isAttachments = Boolean.TRUE.equals(hasAttachments);

        if (isUnread && isAttachments) {
            emails = emailRepository.findByUserAndStatusAndIsReadAndHasAttachments(user, status, false, true, getSort(sortOption));
        } else if (isUnread) {
            emails = emailRepository.findByUserAndStatusAndIsRead(user, status, false, getSort(sortOption));
        } else if (isAttachments) {
            emails = emailRepository.findByUserAndStatusAndHasAttachments(user, status, true, getSort(sortOption));
        } else {
            emails = emailRepository.findByUserAndStatus(user, status, getSort(sortOption));
        }
        
        return emails.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get emails by label (string)
     */
    public List<EmailResponse> getEmailsByLabel(String labelId, String sortOption, Boolean unreadOnly, Boolean hasAttachments) {
        User user = getCurrentUser();
        List<Email> emails;
        
        boolean isUnread = Boolean.TRUE.equals(unreadOnly);
        boolean isAttachments = Boolean.TRUE.equals(hasAttachments);

        if (isUnread && isAttachments) {
            emails = emailRepository.findByUserAndLabelIdsContainingAndIsReadAndHasAttachments(user, labelId, false, true, getSort(sortOption));
        } else if (isUnread) {
            emails = emailRepository.findByUserAndLabelIdsContainingAndIsRead(user, labelId, false, getSort(sortOption));
        } else if (isAttachments) {
            emails = emailRepository.findByUserAndLabelIdsContainingAndHasAttachments(user, labelId, true, getSort(sortOption));
        } else {
            emails = emailRepository.findByUserAndLabelIdsContaining(user, labelId, getSort(sortOption));
        }
        
        return emails.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get email by thread ID
     */
    public EmailResponse getEmailByThreadId(String threadId) {
        User user = getCurrentUser();
        Email email = emailRepository.findByUserAndThreadId(user, threadId)
                .orElseThrow(() -> new RuntimeException("Email not found"));
        return convertToResponse(email);
    }

    /**
     * Create or update email from Gmail thread
     */
    @Transactional
    public EmailResponse createOrUpdateEmail(String threadId, String from, String to, 
                                            String subject, String snippet, String body) {
        User user = getCurrentUser();
        
        Email email = emailRepository.findByUserAndThreadId(user, threadId)
                .orElse(Email.builder()
                        .user(user)
                        .threadId(threadId)
                        .status(EmailStatus.INBOX)
                        .receivedAt(Instant.now())
                        .build());
        
        email.setFrom(from);
        email.setTo(to);
        email.setSubject(subject);
        email.setSnippet(snippet);
        email.setBody(body);
        
        email = emailRepository.save(email);
        return convertToResponse(email);
    }

    /**
     * Update email status
     */
    @Transactional
    public EmailResponse updateEmailStatus(Long id, UpdateEmailStatusRequest request) {
        User user = getCurrentUser();
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email not found"));
        
        if (!email.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        email.setStatus(request.getStatus());
        email = emailRepository.save(email);
        
        log.info("Updated email {} status to {}", id, request.getStatus());
        return convertToResponse(email);
    }

    /**
     * Snooze email until specified time
     */
    @Transactional
    public EmailResponse snoozeEmail(Long id, SnoozeEmailRequest request) {
        User user = getCurrentUser();
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email not found"));
        
        if (!email.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        // Save current status to restore later
        email.setPreviousStatus(email.getStatus());
        email.setStatus(EmailStatus.SNOOZED);
        email.setSnoozedUntil(request.getSnoozeUntil());
        
        email = emailRepository.save(email);
        log.info("Snoozed email {} until {}", id, request.getSnoozeUntil());
        
        return convertToResponse(email);
    }

    /**
     * Snooze email by thread ID (convenience method)
     * Auto-syncs from Gmail if email doesn't exist in database
     */
    @Transactional
    public EmailResponse snoozeEmailByThreadId(String threadId, SnoozeEmailRequest request) {
        User user = getCurrentUser();
        
        // Try to find existing email, if not found, sync from Gmail first
        Email email = emailRepository.findByUserAndThreadId(user, threadId)
                .orElseGet(() -> {
                    log.info("Email not found in database, syncing from Gmail: {}", threadId);
                    return syncEmailFromGmail(user, threadId);
                });
        
        // Save current status to restore later
        email.setPreviousStatus(email.getStatus());
        email.setStatus(EmailStatus.SNOOZED);
        email.setSnoozedUntil(request.getSnoozeUntil());
        
        email = emailRepository.save(email);
        log.info("Snoozed email (threadId={}) until {}", threadId, request.getSnoozeUntil());
        
        return convertToResponse(email);
    }

    /**
     * Sync email from Gmail to database
     * Made public to allow bulk sync
     */
    public Email syncEmailFromGmail(User user, String threadId) {
        try {
            // Fetch thread detail from Gmail
            ThreadDetailResponse threadDetail = proxyMailService.getThreadDetail(threadId).block();
            return saveThreadToDatabase(user, threadDetail);
        } catch (Exception e) {
            log.error("Failed to sync email from Gmail: {}", threadId, e);
            throw new RuntimeException("Failed to sync email from Gmail: " + e.getMessage(), e);
        }
    }

    /**
     * Helper to save a thread response to database
     */
    @Transactional
    public Email saveThreadToDatabase(User user, ThreadDetailResponse threadDetail) {
         if (threadDetail == null || threadDetail.getMessages() == null || threadDetail.getMessages().isEmpty()) {
             return null;
         }
         
         String threadId = threadDetail.getId();
         
         var firstMsg = threadDetail.getMessages().get(0);
         
         var labelIds = threadDetail.getLabelIds();
         boolean isRead = labelIds == null || !labelIds.contains("UNREAD");
         boolean isStarred = labelIds != null && labelIds.contains("STARRED"); 

         String labelIdsString = null;
         if (labelIds != null && !labelIds.isEmpty()) {
             labelIdsString = String.join(",", labelIds);
         }
         
         // Check for attachments
         boolean hasAttachments = false;
         for (var msg : threadDetail.getMessages()) {
             if (msg.getAttachments() != null && !msg.getAttachments().isEmpty()) {
                 hasAttachments = true;
                 break;
             }
         }

         // Parse date
         Instant receivedAt = Instant.now();
         if (firstMsg.getDate() != null) {
             try {
                receivedAt = Instant.parse(firstMsg.getDate());
             } catch (Exception e) {
                 // Fallback
             }
         }
         
         Email email = emailRepository.findByUserAndThreadId(user, threadId)
                 .orElse(Email.builder()
                         .user(user)
                         .threadId(threadId)
                         .build());
         
         email.setFrom(firstMsg.getFrom());
         email.setTo(firstMsg.getTo());
         email.setSubject(firstMsg.getSubject());
         email.setSnippet(threadDetail.getSnippet());
         email.setBody(firstMsg.getTextBody() != null ? firstMsg.getTextBody() : firstMsg.getHtmlBody());
         email.setStatus(EmailStatus.INBOX); 
         email.setLabelIds(labelIdsString); 
         email.setIsRead(isRead);
         email.setIsStarred(isStarred);
         email.setHasAttachments(hasAttachments);
         email.setReceivedAt(receivedAt);
         
         return emailRepository.save(email);
    }

    /**
     * Unsnooze email (restore to previous status)
     */
    @Transactional
    public EmailResponse unsnoozeEmail(Long id) {
        User user = getCurrentUser();
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email not found"));
        
        if (!email.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        EmailStatus restoreStatus = email.getPreviousStatus() != null 
                ? email.getPreviousStatus() 
                : EmailStatus.INBOX;
        
        email.setStatus(restoreStatus);
        email.setSnoozedUntil(null);
        email.setPreviousStatus(null);
        
        email = emailRepository.save(email);
        log.info("Unsnoozed email {} back to {}", id, restoreStatus);
        
        return convertToResponse(email);
    }

    /**
     * Scheduled task to restore snoozed emails
     * Runs every minute
     */
    @Scheduled(fixedRate = 60000) // Every 60 seconds
    @Transactional
    public void restoreSnoozedEmails() {
        Instant now = Instant.now();
        List<Email> snoozedEmails = emailRepository.findAllSnoozedEmailsToRestore(now);
        
        if (!snoozedEmails.isEmpty()) {
            log.info("Found {} snoozed emails to restore", snoozedEmails.size());
            
            for (Email email : snoozedEmails) {
                EmailStatus restoreStatus = email.getPreviousStatus() != null 
                        ? email.getPreviousStatus() 
                        : EmailStatus.INBOX;
                
                email.setStatus(restoreStatus);
                email.setSnoozedUntil(null);
                email.setPreviousStatus(null);
                
                emailRepository.save(email);
                log.info("Auto-restored email {} from snooze to {}", email.getId(), restoreStatus);
            }
        }
    }

    /**
     * Delete email
     */
    @Transactional
    public void deleteEmail(Long id) {
        User user = getCurrentUser();
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email not found"));
        
        if (!email.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        emailRepository.delete(email);
        log.info("Deleted email {}", id);
    }

    /**
     * Update email read status
     */
    @Transactional
    public EmailResponse updateEmailRead(Long id, Boolean isRead) {
        User user = getCurrentUser();
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email not found"));
        
        if (!email.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        email.setIsRead(isRead);
        email = emailRepository.save(email);
        
        log.info("Updated email {} read status to {}", id, isRead);
        return convertToResponse(email);
    }

    /**
     * Update email starred status
     */
    @Transactional
    public EmailResponse updateEmailStarred(Long id, Boolean isStarred) {
        User user = getCurrentUser();
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email not found"));
        
        if (!email.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        email.setIsStarred(isStarred);
        email = emailRepository.save(email);
        
        log.info("Updated email {} starred status to {}", id, isStarred);
        return convertToResponse(email);
    }

    private EmailResponse convertToResponse(Email email) {
        return EmailResponse.builder()
                .id(email.getId())
                .threadId(email.getThreadId())
                .from(email.getFrom())
                .to(email.getTo())
                .subject(email.getSubject())
                .snippet(email.getSnippet())
                .body(email.getBody())
                .summary(email.getSummary())
                .status(email.getStatus())
                .snoozedUntil(email.getSnoozedUntil())
                .isRead(email.getIsRead())
                .isStarred(email.getIsStarred())
                .hasAttachments(email.getHasAttachments())
                .receivedAt(email.getReceivedAt())
                .createdAt(email.getCreatedAt())
                .updatedAt(email.getUpdatedAt())
                .build();
    }

    @Transactional
    public void saveThreadToDatabase(User user, ThreadDetailResponse threadDetail) {
        if (threadDetail == null || threadDetail.getMessages() == null || threadDetail.getMessages().isEmpty()) {
            return;
        }

        var firstMsg = threadDetail.getMessages().get(0);

        // 1. Chuẩn bị nội dung Text (Ưu tiên text/plain, nếu không có thì lấy html strip tag)
        String rawBody = firstMsg.getTextBody() != null ? firstMsg.getTextBody() : firstMsg.getHtmlBody();
        String cleanBody = rawBody != null ? rawBody.replaceAll("\\<.*?\\>", "").trim() : "";
        String subject = firstMsg.getSubject() != null ? firstMsg.getSubject() : "";

        // 2. Tạo nội dung để Embedding
        // Mẹo: Lặp lại Subject 2-3 lần để tăng trọng số khi tìm kiếm
        String contentToEmbed = "Subject: " + subject + ". " + subject + ". Body: " +
                (cleanBody.length() > 2000 ? cleanBody.substring(0, 2000) : cleanBody);

        // 3. Gọi AI tạo Vector (Block ở đây vì đang trong luồng xử lý đồng bộ của doOnNext)
        PGvector embedding = embeddingService.getEmbedding(contentToEmbed).block();

        // 4. Lưu vào DB
        Email email = emailRepository.findByUserAndThreadId(user, threadDetail.getId())
                .orElse(Email.builder()
                        .user(user)
                        .threadId(threadDetail.getId())
                        .build());

        email.setSubject(subject);
        email.setSnippet(threadDetail.getSnippet());
        email.setBody(cleanBody); // Lưu body đã làm sạch hoặc raw tùy bạn
        email.setEmbedding(embedding); // <--- LƯU VECTOR VÀO ĐÂY

        // Parse ngày tháng (đơn giản hóa)
        try {
            if (firstMsg.getDate() != null) {
                // Bạn cần một hàm parse date chuẩn RFC_1123 ở đây, ví dụ: Instant.parse(...)
                // email.setReceivedAt(...);
                email.setReceivedAt(Instant.now()); // Tạm thời để now nếu chưa có parser
            }
        } catch (Exception e) {
            email.setReceivedAt(Instant.now());
        }

        emailRepository.save(email);
        log.debug("Saved and embedded thread: {}", threadDetail.getId());
    }
}
