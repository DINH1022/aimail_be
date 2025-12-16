package com.example.aimailbox.controller;

import com.example.aimailbox.dto.request.SnoozeEmailRequest;
import com.example.aimailbox.dto.request.UpdateEmailStatusRequest;
import com.example.aimailbox.dto.request.UpdateReadRequest;
import com.example.aimailbox.dto.request.UpdateStarredRequest;
import com.example.aimailbox.dto.response.EmailResponse;
import com.example.aimailbox.model.EmailStatus;
import com.example.aimailbox.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    /**
     * Get all emails for current user
     */
    @GetMapping
    public ResponseEntity<List<EmailResponse>> getAllEmails(
            @RequestParam(required = false) String label,
            @RequestParam(required = false) EmailStatus status,
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly,
            @RequestParam(required = false, defaultValue = "newest") String sort) {
        
        if (label != null && !label.isEmpty()) {
            return ResponseEntity.ok(emailService.getEmailsByLabel(label, sort, unreadOnly));
        }

        if (status != null) {
            return ResponseEntity.ok(emailService.getEmailsByStatus(status, sort, unreadOnly));
        }
        return ResponseEntity.ok(emailService.getAllEmails(sort, unreadOnly));
    }

    /**
     * Get email by thread ID (with database ID)
     */
    @GetMapping("/thread/{threadId}")
    public ResponseEntity<EmailResponse> getEmailByThreadId(@PathVariable String threadId) {
        return ResponseEntity.ok(emailService.getEmailByThreadId(threadId));
    }

    /**
     * Snooze email by thread ID (convenience endpoint)
     */
    @PostMapping("/thread/{threadId}/snooze")
    public ResponseEntity<EmailResponse> snoozeEmailByThreadId(
            @PathVariable String threadId,
            @Valid @RequestBody SnoozeEmailRequest request) {
        
        return ResponseEntity.ok(emailService.snoozeEmailByThreadId(threadId, request));
    }

    /**
     * Get email by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmailResponse> getEmailById(@PathVariable Long id) {
        // Temporarily use unsnoozeEmail logic to get the email
        // This should be replaced with a proper getById method
        EmailResponse email = emailService.getEmailsByStatus(null, null, false).stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Email not found"));
        return ResponseEntity.ok(email);
    }

    /**
     * Update email status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<EmailResponse> updateEmailStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmailStatusRequest request) {
        
        return ResponseEntity.ok(emailService.updateEmailStatus(id, request));
    }

    /**
     * Snooze email until specified time
     */
    @PostMapping("/{id}/snooze")
    public ResponseEntity<EmailResponse> snoozeEmail(
            @PathVariable Long id,
            @Valid @RequestBody SnoozeEmailRequest request) {
        
        return ResponseEntity.ok(emailService.snoozeEmail(id, request));
    }

    /**
     * Unsnooze email (restore immediately)
     */
    @PostMapping("/{id}/unsnooze")
    public ResponseEntity<EmailResponse> unsnoozeEmail(@PathVariable Long id) {
        return ResponseEntity.ok(emailService.unsnoozeEmail(id));
    }

    /**
     * Delete email
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmail(@PathVariable Long id) {
        emailService.deleteEmail(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Update email read status
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<EmailResponse> updateEmailRead(
            @PathVariable Long id,
            @RequestBody UpdateReadRequest request) {
        
        return ResponseEntity.ok(emailService.updateEmailRead(id, request.getIsRead()));
    }

    /**
     * Update email starred status
     */
    @PatchMapping("/{id}/starred")
    public ResponseEntity<EmailResponse> updateEmailStarred(
            @PathVariable Long id,
            @RequestBody UpdateStarredRequest request) {
        
        return ResponseEntity.ok(emailService.updateEmailStarred(id, request.getIsStarred()));
    }
}
