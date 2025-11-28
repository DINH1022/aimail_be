package com.example.aimailbox.controller;

import com.example.aimailbox.dto.request.EmailSendRequest;
import com.example.aimailbox.dto.response.GmailSendResponse;
import com.example.aimailbox.dto.response.ThreadDetailResponse;
import com.example.aimailbox.service.ProxyMailService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Base64;

@RestController
@RequestMapping("/emails")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class MailController {
    ProxyMailService proxyMailService;
    @GetMapping("/{id}")
    public Mono<ThreadDetailResponse> getEmailDetail (@PathVariable String id) {
        return proxyMailService.getThreadDetail(id);
    }
    @GetMapping("/{messageId}/attachments/{attachmentId}")
    public Mono<ResponseEntity<byte[]>> getEmailAttachment(
            @PathVariable String messageId,
            @PathVariable String attachmentId,
            @RequestParam String filename,
            @RequestParam String mimeType
    ) {
        return proxyMailService.getAttachment(messageId,attachmentId)
                .map( attachmentResponse->
                {
                    byte[] attachmentData = Base64.getUrlDecoder().decode(attachmentResponse.getData());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+filename+"\"")
                            .header("Content-Type",mimeType)
                            .header("Content-Length", String.valueOf(attachmentData.length))
                            .body(attachmentData);
                });
    }
    @PostMapping(value = "/send",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<GmailSendResponse> sendEmail(@ModelAttribute EmailSendRequest request) {
        return proxyMailService.sendEmail(request);
    }
}
