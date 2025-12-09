package com.example.aimailbox.controller;

import com.example.aimailbox.dto.request.EmailSendRequest;
import com.example.aimailbox.dto.request.ModifyEmailRequest;
import com.example.aimailbox.dto.response.GmailSendResponse;
import com.example.aimailbox.dto.response.ThreadDetailResponse;
import com.example.aimailbox.service.ProxyMailService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/emails")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class MailController {
    ProxyMailService proxyMailService;

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
}
