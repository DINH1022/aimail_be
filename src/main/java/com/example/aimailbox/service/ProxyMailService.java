package com.example.aimailbox.service;

import com.example.aimailbox.dto.request.EmailSendRequest;
import com.example.aimailbox.dto.request.LabelCreationRequest;
import com.example.aimailbox.dto.request.ModifyEmailRequest;
import com.example.aimailbox.dto.response.*;
import com.example.aimailbox.dto.response.mail.*;
import com.example.aimailbox.model.User;
import com.example.aimailbox.wrapper.LabelWrapper;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class ProxyMailService {
    final WebClient gmailWebClient;
    final OAuthTokenService oAuthTokenService;
    final WebClient googleGenerativeClient;

    @Value("${google.generative-api-key:}")
    String googleGenerativeApiKey;

    public Mono<List<LabelResponse>> getAllLabels() {
        return gmailWebClient.get()
                .uri("/labels")
                .retrieve()
                .bodyToMono(LabelWrapper.class)
                .map(LabelWrapper::getLabels)
                .defaultIfEmpty(List.of())
                .onErrorMap(e -> new RuntimeException("Failed to fetch labels", e));
    }

    public Mono<LabelDetailResponse> getLabel(String id) {
        return gmailWebClient.get()
                .uri("/labels/{id}", id)
                .retrieve()
                .bodyToMono(LabelDetailResponse.class)
                .defaultIfEmpty(new LabelDetailResponse())
                .onErrorMap(e -> new RuntimeException("Failed to fetch label details", e));
    }

    public Mono<LabelDetailResponse> createLabel(LabelCreationRequest request) {
        return gmailWebClient.post()
                .uri("/labels")
                 .bodyValue(request)
                 .retrieve()
                 .bodyToMono(LabelDetailResponse.class)
                .onErrorMap(e -> new RuntimeException("Failed to create label", e));
    }

    public Mono<Void> deleteLabel(String id) {
        return gmailWebClient.delete()
                .uri("/labels/{id}", id)
                 .retrieve()
                 .bodyToMono(Void.class)
                .onErrorMap(e -> new RuntimeException("Failed to delete label", e));
    }

    public Mono<ListThreadResponse> getListThreads(Integer maxResults, String pageToken, String query, String labelId,
            Boolean includeSpamTrash) {
        return gmailWebClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/threads");
                    if (maxResults != null) {
                        uriBuilder.queryParam("maxResults", maxResults);
                    }
                    if (pageToken != null && !pageToken.isEmpty()) {
                        uriBuilder.queryParam("pageToken", pageToken);
                    }
                    if (query != null && !query.isEmpty()) {
                        uriBuilder.queryParam("q", query);
                    }
                    if (labelId != null && !labelId.isEmpty()) {
                        uriBuilder.queryParam("labelIds", labelId);
                    }
                    uriBuilder.queryParam("includeSpamTrash", includeSpamTrash);
                    return uriBuilder.build();
                })
                 .retrieve()
                .bodyToMono(ListThreadResponse.class)
                .defaultIfEmpty(new ListThreadResponse())
                .onErrorMap(e -> new RuntimeException("Failed to fetch messages", e));
    }

    public Mono<ThreadDetailResponse> getThreadDetail(String id) {
        return gmailWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/threads/{id}")
                        .queryParam("format", "full")
                        .build(id))
                .retrieve()
                .bodyToMono(ThreadDetail.class)
                .map(fullThread -> this.parseListMessage(fullThread))
                .onErrorMap(e -> new RuntimeException("Failed to fetch thread details", e));
    }

    public Mono<AttachmentResponse> getAttachment(String messageId, String attachmentId) {
        return gmailWebClient.get()
                .uri("/messages/{messageId}/attachments/{attachmentId}", messageId, attachmentId)
                 .retrieve()
                .bodyToMono(AttachmentResponse.class)
                .onErrorMap(e -> new RuntimeException("Failed to fetch attachment", e));
    }

    public byte[] getAttachmentBytes(String messageId, String attachmentId) {
        try {
            AttachmentResponse attachmentResponse = getAttachment(messageId, attachmentId).block();

            if (attachmentResponse == null) {
                throw new RuntimeException("Attachment response is null");
            }

            if (attachmentResponse.getData() == null || attachmentResponse.getData().isEmpty()) {
                throw new RuntimeException("Attachment data is null or empty");
            }
            byte[] decodedData = Base64.getUrlDecoder().decode(attachmentResponse.getData());

            return decodedData;
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid base64 data in attachment", e);
        } catch (Exception e) {
            log.error("Failed to download attachment", e);
            throw new RuntimeException("Failed to download attachment: " + e.getMessage(), e);
        }
    }

    public Mono<Flux<DataBuffer>> streamAttachment(String messageId, String attachmentId) {
        return getAttachment(messageId, attachmentId)
                .map(attachmentResponse -> {
                    byte[] decodedData = Base64.getUrlDecoder().decode(attachmentResponse.getData());
                    DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
                    int chunkSize = 8192;

                    return Flux.range(0, (decodedData.length + chunkSize - 1) / chunkSize)
                            .map(chunkIndex -> {
                                int start = chunkIndex * chunkSize;
                                int end = Math.min(start + chunkSize, decodedData.length);
                                int length = end - start;

                                DataBuffer buffer = bufferFactory.allocateBuffer(length);
                                buffer.write(decodedData, start, length);
                                return buffer;
                            })
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .onErrorMap(e -> new RuntimeException("Failed to stream attachment", e));
    }

    public Mono<GmailSendResponse> sendEmail(EmailSendRequest request) {
        boolean hasRecipients = (request.getTo() != null && !request.getTo().isEmpty()) ||
                (request.getCc() != null && !request.getCc().isEmpty()) ||
                (request.getBcc() != null && !request.getBcc().isEmpty());

        if (!hasRecipients) {
            return Mono
                    .error(new IllegalArgumentException("At least one recipient (To, Cc, or Bcc) must be specified"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            log.error("No authenticated user found in SecurityContext");
            return Mono.error(new RuntimeException("You must be logged in to send emails"));
        }

        User currentUser = (User) auth.getPrincipal();
        if (currentUser.getGoogleAccessToken() == null) {
            log.error("User {} attempted to send email without Google OAuth token", currentUser.getEmail());
            return Mono.error(new RuntimeException("You must sign in with Google to send emails. Please logout and login using 'Sign in with Google'."));
        }

        if (request.getInReplyToMessageId() != null && !request.getInReplyToMessageId().isEmpty()) {
            String subject = request.getSubject();
            String replySubject = subject.startsWith("Re:") ? subject : "Re: " + subject;
            request.setSubject(replySubject);
        }
        
        return Mono.fromCallable(() -> createMimeMessage(request))
                .flatMap(email -> sendToGmailApi(email, request.getThreadId()))
                .contextWrite(ctx -> ctx.put(Authentication.class, auth))
                .onErrorMap(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("401")) {
                        log.error("Gmail API returned 401 Unauthorized for user {}", currentUser.getEmail());
                        return new RuntimeException("Gmail authentication failed. Please logout and login again with Google.");
                    }
                    return e;
                });
    }

    public Mono<String> modifyMessageLabels(ModifyEmailRequest request) {
        Map<String, Object> payload = new HashMap<>();
        if (request.getAddLabelIds() != null) {
            payload.put("addLabelIds", request.getAddLabelIds());
        }
        if (request.getRemoveLabelIds() != null) {
            payload.put("removeLabelIds", request.getRemoveLabelIds());
        }
        return gmailWebClient.post()
                .uri("/threads/{id}/modify", request.getThreadId())
                 .bodyValue(payload)
                 .retrieve()
                 .bodyToMono(String.class)
                .map(response -> "Labels modified successfully")
                .onErrorMap(e -> new RuntimeException("Failed to modify message labels", e));
    }

    public Mono<Void> deleteMessage(String messageId) {
        return gmailWebClient.delete()
                .uri("/messages/{id}", messageId)
                 .retrieve()
                 .bodyToMono(Void.class)
                .onErrorMap(e -> new RuntimeException("Failed to delete message", e));
    }

    public Mono<Void> deleteMail(String mailId) {
        return gmailWebClient.delete()
                .uri("/threads/{id}", mailId)
                 .retrieve()
                 .bodyToMono(Void.class)
                .onErrorMap(e -> new RuntimeException("Failed to delete message", e));
    }

    private Mono<GmailSendResponse> sendToGmailApi(MimeMessage email, String threadId) {
        return Mono.fromCallable(() -> {
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                email.writeTo(buffer);
                byte[] rawBytes = buffer.toByteArray();
                String encodedEmail = Base64.getUrlEncoder().encodeToString(rawBytes);

                Map<String, String> payload;
                if (threadId != null) {
                    payload = Map.of("raw", encodedEmail, "threadId", threadId);
                } else {
                    payload = Map.of("raw", encodedEmail);
                }
                return payload;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create email payload", e);
            }
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(payload -> gmailWebClient.post()
                        .uri("/messages/send")
                         .bodyValue(payload)
                         .retrieve()
                         .bodyToMono(GmailSendResponse.class));
    }

    private ThreadDetailResponse parseListMessage(ThreadDetail threadDetail) {
        ThreadDetailResponse threadDetailResponse = ThreadDetailResponse.builder()
                .id(threadDetail.getId())
                .snippet(threadDetail.getSnippet())
                .labelIds(threadDetail.getLabelIds())
                .messages(new ArrayList<>())
                .build();
        if (threadDetail.getMessages() != null) {
            for (Message message : threadDetail.getMessages()) {
                MessageDetailResponse messageDetailResponse = parseMessage(message);
                threadDetailResponse.getMessages().add(messageDetailResponse);
            }
        }
        return threadDetailResponse;
    }

    private MessageDetailResponse parseMessage(Message message) {
        MessageDetailResponse messageDetailResponse = MessageDetailResponse.builder()
                .id(message.getId())
                .threadId(message.getThreadId())
                .snippet(message.getSnippet())
                .attachments(new ArrayList<>())
                .build();
        if (message.getPayload() != null) {
            List<MessagePartHeader> headers = message.getPayload().getHeaders();
            messageDetailResponse.setMessageId(getHeader(headers, "Message-ID"));
            messageDetailResponse.setFrom(getHeader(headers, "From"));
            messageDetailResponse.setTo(getHeader(headers, "To"));
            messageDetailResponse.setCc(getHeader(headers, "Cc"));
            messageDetailResponse.setBcc(getHeader(headers, "Bcc"));
            messageDetailResponse.setSubject(getHeader(headers, "Subject"));
            String dateStr = getHeader(headers, "Date");
            if (dateStr != null && !dateStr.isEmpty()) {
                try {
                    Instant instant = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(dateStr));
                    messageDetailResponse.setDate(instant.atOffset(ZoneOffset.UTC).toString());
                } catch (Exception e) {
                    messageDetailResponse.setDate(dateStr);
                }
            }
            traverseParts(message.getPayload(), messageDetailResponse);
        }
        return messageDetailResponse;
    }

    private void traverseParts(MessagePart part, MessageDetailResponse messageDetailResponse) {
        if (part == null)
            return;
        if (part.getBody() != null && part.getBody().getData() != null) {
            String decoded = decodeBase64UrlSafe(part.getBody().getData());
            switch (part.getMimeType().toLowerCase()) {
                case "text/plain":
                    if (messageDetailResponse.getTextBody() == null) {
                        messageDetailResponse.setTextBody(decoded);
                    }
                    break;
                case "text/html":
                    if (messageDetailResponse.getHtmlBody() == null) {
                        messageDetailResponse.setHtmlBody(decoded);
                    }
                    break;
            }
        }
        if (part.getFilename() != null && !part.getFilename().isBlank()) {
            messageDetailResponse.getAttachments().add(new Attachment(
                    part.getFilename(),
                    part.getMimeType(),
                    part.getBody() != null ? part.getBody().getAttachmentId() : null));
        }
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                traverseParts(child, messageDetailResponse);
            }
        }
    }

    private String getHeader(List<MessagePartHeader> headers, String name) {
        if (headers == null)
            return null;
        for (MessagePartHeader h : headers) {
            if (name.equalsIgnoreCase(h.getName()))
                return h.getValue();
        }
        return null;
    }

    private String decodeBase64UrlSafe(String data) {
        if (data == null)
            return null;
        byte[] decoded = Base64.getUrlDecoder().decode(data);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private MimeMessage createMimeMessage(EmailSendRequest request) throws MessagingException, IOException {
        Session session = Session.getDefaultInstance(new Properties(), null);
        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress("me"));
        if (request.getTo() != null && !request.getTo().isEmpty()) {
            email.addRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(request.getTo()));
        }
        if (request.getBcc() != null && !request.getBcc().isEmpty()) {
            email.addRecipients(jakarta.mail.Message.RecipientType.BCC, InternetAddress.parse(request.getBcc()));
        }
        if (request.getCc() != null && !request.getCc().isEmpty()) {
            email.addRecipients(jakarta.mail.Message.RecipientType.CC, InternetAddress.parse(request.getCc()));
        }
        email.setSubject(request.getSubject(), "UTF-8");
        if (request.getInReplyToMessageId() != null && !request.getInReplyToMessageId().isEmpty()) {
            email.setHeader("In-Reply-To", request.getInReplyToMessageId());
            email.setHeader("References", request.getInReplyToMessageId());
        }

        MimeMultipart rootMultipart = new MimeMultipart("mixed");
        MimeBodyPart contentBodyPart = getMimeBodyPart(request);
        rootMultipart.addBodyPart(contentBodyPart);
        if (request.getAttachment() != null) {
            for (MultipartFile file : request.getAttachment()) {
                if (file == null || file.isEmpty())
                    continue;
                try {
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    String fileName = file.getOriginalFilename();

                    try {
                        jakarta.activation.DataSource ds = new jakarta.mail.util.ByteArrayDataSource(file.getBytes(),
                                file.getContentType() != null ? file.getContentType() : "application/octet-stream");
                        attachmentPart.setDataHandler(new jakarta.activation.DataHandler(ds));
                    } catch (Exception ex) {
                        attachmentPart.setContent(file.getBytes(),
                                file.getContentType() != null ? file.getContentType() : "application/octet-stream");
                    }

                    if (fileName != null) {
                        try {
                            attachmentPart.setFileName(MimeUtility.encodeText(fileName, "UTF-8", null));
                        } catch (Exception e) {
                            attachmentPart.setFileName(fileName);
                        }
                    }
                    attachmentPart.setDisposition(jakarta.mail.Part.ATTACHMENT);
                    rootMultipart.addBodyPart(attachmentPart);
                } catch (Exception e) {
                    throw new IOException("Failed to attach file " + file.getOriginalFilename(), e);
                }
            }
        }
        email.setContent(rootMultipart);
        return email;

    }

    private static MimeBodyPart getMimeBodyPart(EmailSendRequest request) throws MessagingException {
        MimeBodyPart contentBodyPart = new MimeBodyPart();
        MimeMultipart alternativeMultipart = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        String content = request.getContent() != null ? request.getContent() : "";
        String plainText = request.isHtml() ? content.replaceAll("<.*?>", "") : content;
        textPart.setText(plainText, "UTF-8");
        alternativeMultipart.addBodyPart(textPart);
        if (request.isHtml()) {
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(content, "text/html; charset=UTF-8");
            alternativeMultipart.addBodyPart(htmlPart);
        }
        contentBodyPart.setContent(alternativeMultipart);
        return contentBodyPart;
    }

    private Mono<EmailSummaryResponse> generateSummary(MessageDetailResponse message) {
        String content = message.getTextBody() != null
                ? message.getTextBody()
                : message.getHtmlBody();

        if (content == null || content.isBlank()) {
            log.info("generateSummary: no content to summarize for messageId={}", message.getId());
            return Mono.just(new EmailSummaryResponse("No content to summarize"));
        }

        String prompt = """
        Summarize the following email clearly and concisely.
        Provide ONLY the summary text, with no explanation.

        Email:
        %s
        """.formatted(content);

        if (googleGenerativeApiKey == null || googleGenerativeApiKey.isBlank()) {
            log.warn("generateSummary: googleGenerativeApiKey is not set; Gemini call may fail");
        }

        String fullUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
                + "?key=" + googleGenerativeApiKey;

        log.info("generateSummary: calling Gemini at {} promptLen={}", fullUrl, prompt.length());

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        return googleGenerativeClient.post()
                .uri(fullUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .doOnNext(resp -> log.info("generateSummary: Gemini response preview={}",
                        resp.getText() == null ? "(null)" : resp.getText().substring(0, Math.min(200, resp.getText().length()))))
                .map(resp -> {
                    String summaryText = resp.getText() != null ? resp.getText() : "Failed to summarize";

                    // build one-line subject (fallback to subject or snippet)
                    String oneLine = message.getSubject() != null ? message.getSubject().replaceAll("\n", " ").trim() : null;
                    if (oneLine != null && oneLine.length() > 120) oneLine = oneLine.substring(0, 117) + "...";

                    // create bullets from summary by splitting into lines / sentences, limit to 5
                    List<String> bullets = new ArrayList<>();
                    if (summaryText != null && !summaryText.isBlank()) {
                        String[] parts = summaryText.split("\\r?\\n|(?<=[.!?])\\s+");
                        for (String p : parts) {
                            String t = p.trim();
                            if (!t.isEmpty()) {
                                bullets.add(t);
                                if (bullets.size() >= 5) break;
                            }
                        }
                    }

                    return EmailSummaryResponse.builder()
                            .messageId(message.getId())
                            .subject(message.getSubject())
                            .from(message.getFrom())
                            .to(message.getTo())
                            .date(message.getDate())
                            .oneLineSubject(oneLine)
                            .bullets(bullets.isEmpty() ? null : bullets)
                            .summary(summaryText)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("generateSummary: Gemini request failed", e);
                    return Mono.just(new EmailSummaryResponse("Failed to summarize"));
                });
    }

    public Mono<EmailSummaryResponse> summarizeText(String text) {
        if (text == null || text.isBlank()) {
            log.info("summarizeText: empty input");
            return Mono.just(new EmailSummaryResponse("No content to summarize"));
        }

        String prompt = """
        Summarize the following email clearly and concisely.
        Provide ONLY the summary text, with no explanation.

        Email:
        %s
        """.formatted(text);

        if (googleGenerativeApiKey == null || googleGenerativeApiKey.isBlank()) {
            log.warn("summarizeText: googleGenerativeApiKey is not set; Gemini call may fail");
        }

        String fullUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
                + "?key=" + googleGenerativeApiKey;

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        return googleGenerativeClient.post()
                .uri(fullUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .doOnNext(resp -> log.info("summarizeText: Gemini response preview={}",
                        resp.getText() == null ? "(null)" : resp.getText().substring(0, Math.min(200, resp.getText().length()))))
                .map(resp -> new EmailSummaryResponse(
                        resp.getText() != null ? resp.getText() : "Failed to summarize"
                ))
                .onErrorResume(e -> {
                    log.error("summarizeText: Gemini request failed", e);
                    return Mono.just(new EmailSummaryResponse("Failed to summarize"));
                });
    }

    public Mono<EmailSummaryResponse> summarizeMessage(String messageId) {
        return summarizeMessage(messageId, null);
    }

    // New overload: accepts an optional Google access token (Bearer) to use for the Gmail API request
    public Mono<EmailSummaryResponse> summarizeMessage(String messageId, String bearerToken) {
        var uriSpec = gmailWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/messages/{id}")
                        .queryParam("format", "full")
                        .build(messageId));

        if (bearerToken != null && !bearerToken.isBlank()) {
            log.info("summarizeMessage: using provided bearer token for Gmail request (messageId={})", messageId);
            uriSpec = uriSpec.headers(h -> h.setBearerAuth(bearerToken));
        } else {
            log.info("summarizeMessage: no bearer token provided, using app SecurityContext for Gmail request (messageId={})", messageId);
        }

        return uriSpec
                .retrieve()
                .bodyToMono(Message.class)
                .map(this::parseMessage)
                .flatMap(this::generateSummary)
                .onErrorReturn(new EmailSummaryResponse("Failed to load message"));
    }

}
