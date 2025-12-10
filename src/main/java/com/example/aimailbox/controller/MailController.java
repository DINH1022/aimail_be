package com.example.aimailbox.controller;

import com.example.aimailbox.dto.request.EmailSendRequest;
import com.example.aimailbox.dto.request.ModifyEmailRequest;
import com.example.aimailbox.dto.response.EmailSummaryResponse;
import com.example.aimailbox.dto.response.GmailSendResponse;
import com.example.aimailbox.dto.response.ThreadDetailResponse;
import com.example.aimailbox.service.ProxyMailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/emails")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class MailController {
    ProxyMailService proxyMailService;
    private static final Logger log = LoggerFactory.getLogger(MailController.class);

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
        String authHeader = request.getHeader("Authorization");
        String loggedAuth = authHeader;
        if (loggedAuth == null) loggedAuth = "(none)";
        else if (loggedAuth.length() > 64) loggedAuth = loggedAuth.substring(0, 64) + "...";
        log.info("Incoming summary request for messageId={} Authorization={}", messageId, loggedAuth);

        // Log current SecurityContext (may be empty if filter not applied)
        try {
            var sc = org.springframework.security.core.context.SecurityContextHolder.getContext();
            if (sc == null) {
                log.warn("SecurityContextHolder.getContext() returned null in controller");
            } else {
                var auth = sc.getAuthentication();
                if (auth == null) {
                    log.warn("No Authentication in SecurityContextHolder in controller");
                } else {
                    Object principal = auth.getPrincipal();
                    log.info("Controller SecurityContext authentication present: name={} principalClass={} authenticated={}",
                            auth.getName(), principal == null ? "null" : principal.getClass().getName(), auth.isAuthenticated());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to inspect SecurityContext in controller", e);
        }

        // If caller supplied an Authorization header (Google access token), extract and pass it to the service
        String bearerToken = null;
        if (authHeader != null) {
            if (authHeader.toLowerCase().startsWith("bearer ")) {
                bearerToken = authHeader.substring(7).trim();
            } else {
                bearerToken = authHeader.trim();
            }
        }

        return proxyMailService.summarizeMessage(messageId, bearerToken);
    }

    @PostMapping(value = "/summarize-text", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Mono<EmailSummaryResponse> summarizeTextRaw(@RequestBody String text) {
        log.info("Incoming raw-text summary request (first64)={}", text == null ? "(null)" : (text.length() > 64 ? text.substring(0,64) + "..." : text));
        return proxyMailService.summarizeText(text);
    }
}