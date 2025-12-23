package com.example.aimailbox.controller;

import com.example.aimailbox.dto.request.EmailSendRequest;
import com.example.aimailbox.dto.request.ModifyEmailRequest;
import com.example.aimailbox.dto.response.EmailResponse;
import com.example.aimailbox.dto.response.EmailSummaryResponse;
import com.example.aimailbox.dto.response.GmailSendResponse;
import com.example.aimailbox.dto.response.ThreadDetailResponse;
import com.example.aimailbox.service.FuzzySearchService;
import com.example.aimailbox.service.ProxyMailService;
import com.example.aimailbox.service.SematicSearchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/emails")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class MailController {
    ProxyMailService proxyMailService;
    FuzzySearchService fuzzySearchService;
    private static final Logger log = LoggerFactory.getLogger(MailController.class);
    private final SematicSearchService sematicSearchService;

    @GetMapping("/{id}")
    public Mono<ThreadDetailResponse> getEmailDetail(@PathVariable String id) {
        return proxyMailService.getThreadDetail(id);
    }

    @GetMapping("/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> getEmailAttachment(
            @PathVariable String messageId,
            @PathVariable String attachmentId,
            @RequestParam String filename,
            @RequestParam String mimeType) {
        try {
            byte[] attachmentData = proxyMailService.getAttachmentBytes(messageId, attachmentId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(filename)
                            .build());
            headers.setContentType(MediaType.parseMediaType(mimeType));
            headers.set("Access-Control-Expose-Headers", "Content-Disposition");
            headers.setContentLength(attachmentData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(attachmentData);
        } catch (Exception error) {
            error.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error: " + error.getMessage()).getBytes());
        }
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<GmailSendResponse> sendEmail(@ModelAttribute EmailSendRequest request) {
        return proxyMailService.sendEmail(request);
    }

    @PostMapping("/modify")
    public Mono<String> modifyEmail(@RequestBody ModifyEmailRequest request) {
        return proxyMailService.modifyMessageLabels(request);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteMail(@PathVariable String id) {
        return proxyMailService.deleteMail(id);
    }

    @DeleteMapping("/message/{id}")
    public Mono<Void> deleteMessage(@PathVariable String id) {
        return proxyMailService.deleteMessage(id);
    }

    @GetMapping("/{messageId}/summary")
    public Mono<EmailSummaryResponse> summarizeMessage(@PathVariable String messageId, HttpServletRequest request) {
        // Try to obtain Google access token from the current authenticated user first
        String bearerToken = null;
        try {
            var sc = org.springframework.security.core.context.SecurityContextHolder.getContext();
            if (sc != null) {
                var auth = sc.getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof com.example.aimailbox.model.User) {
                    com.example.aimailbox.model.User currentUser = (com.example.aimailbox.model.User) auth.getPrincipal();
                    bearerToken = currentUser.getGoogleAccessToken();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read current user from SecurityContext", e);
        }

        // If no token on the logged-in user, fall back to Authorization header (client-supplied token)
        if (bearerToken == null || bearerToken.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null) {
                if (authHeader.toLowerCase().startsWith("bearer ")) {
                    bearerToken = authHeader.substring(7).trim();
                } else {
                    bearerToken = authHeader.trim();
                }
            }
        }

        String loggedToken = bearerToken == null ? "(none)" : (bearerToken.length() > 64 ? bearerToken.substring(0, 64) + "..." : bearerToken);
        log.info("Incoming summary request for messageId={} using bearerTokenPrefix={}", messageId, loggedToken);

        return proxyMailService.summarizeMessage(messageId, bearerToken);
    }

    @PostMapping(value = "/summarize-text", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Mono<EmailSummaryResponse> summarizeTextRaw(@RequestBody String text) {
        log.info("Incoming raw-text summary request (first64)={}", text == null ? "(null)" : (text.length() > 64 ? text.substring(0,64) + "..." : text));
        return proxyMailService.summarizeText(text);
    }

    @GetMapping("/search")
    public Mono<List<ThreadDetailResponse>> searchEmails(@RequestParam String query) {
        return fuzzySearchService.searchFuzzyEmails(query);
    }
    @GetMapping("/search-sematic")
    public List<EmailResponse> searchSematic(@RequestParam String query) {
        return sematicSearchService.searchSematic(query);
    }
    @PostMapping("/sync")
    public Mono<Void> syncEmails() {
        return fuzzySearchService.refreshData();
    }
    @PostMapping("/sematic-sync")
    public Mono<Void> syncSematicEmail() {
        return sematicSearchService.syncEmails();
    }
}