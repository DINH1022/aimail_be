package com.example.aimailbox.service;

import com.example.aimailbox.dto.request.EmailSendRequest;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class ProxyMailService {
    
    WebClient gmailWebClient;
    OAuthTokenService oAuthTokenService;

    /**
     * Get the authenticated user from security context
     */
    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // Added helper to get a valid access token for the authenticated user
    private String getAccessToken() {
        User user = getAuthenticatedUser();

        try {
            // Ensure user has saved OAuth tokens and refresh if needed
            return oAuthTokenService.getValidAccessToken(user);
        } catch (Exception e) {
            log.warn("User {} does not have valid Google OAuth tokens: {}", user != null ? user.getEmail() : "unknown", e.getMessage());
            throw new RuntimeException("Access denied. Your Google account may not have the necessary Gmail permissions. Please try signing out and signing in again to reauthorize.");
        }
    }

    // Fetch all labels
    public Mono<List<LabelResponse>> getAllLabels() {
        String accessToken = getAccessToken();
        return gmailWebClient.get()
                .uri("/labels")
                .headers(header ->header.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(LabelWrapper.class)
                .map(LabelWrapper::getLabels)
                .defaultIfEmpty(List.of())
                .onErrorMap(e-> new RuntimeException("Failed to fetch labels",e));
    }
    // Fetch label details by ID
    public Mono<LabelDetailResponse>  getLabel(String id) {
        User user = getAuthenticatedUser();
        log.info("Fetching label details for labelId: {} by user: {}", id, user.getEmail());
        
        String accessToken = getAccessToken();
        return gmailWebClient.get()
                .uri("/labels/{id}",id)
                .headers(header ->header.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(LabelDetailResponse.class)
                .doOnSuccess(label -> log.info("Successfully fetched label details for labelId: {} by user: {}", 
                    id, user.getEmail()))
                .doOnError(error -> log.error("Failed to fetch label details for labelId: {} by user: {}", 
                    id, user.getEmail(), error))
                .defaultIfEmpty(new LabelDetailResponse())
                .onErrorMap(e-> new RuntimeException("Failed to fetch label details",e));
    }
    // Fetch list of threads with optional parameters
    public Mono<ListThreadResponse> getListThreads(Integer maxResults
            , String pageToken
            , String query
            , String labelId
            , Boolean includeSpamTrash) {
        User user = getAuthenticatedUser();
        log.info("Fetching threads for user: {}, labelId: {}, query: {}, maxResults: {}, pageToken: {}", 
                user.getEmail(), labelId, query, maxResults, pageToken);
        
        String accessToken = getAccessToken();
        return gmailWebClient.get()
                .uri(uriBuilder ->{
                    uriBuilder.path("/threads");
                    if(maxResults!=null){
                        uriBuilder.queryParam("maxResults",maxResults);
                    }
                    if(pageToken!=null && !pageToken.isEmpty()){
                        uriBuilder.queryParam("pageToken",pageToken);
                    }
                    if(query!=null && !query.isEmpty()){
                        uriBuilder.queryParam("q",query);
                    }
                    if(labelId!=null && !labelId.isEmpty()){
                        uriBuilder.queryParam("labelIds",labelId);
                    }
                    uriBuilder.queryParam("includeSpamTrash",includeSpamTrash);
                    return uriBuilder.build();
                })
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(ListThreadResponse.class)
                .doOnSuccess(response -> log.info("Successfully fetched {} threads for user: {}, labelId: {}", 
                    response != null && response.getThreads() != null ? response.getThreads().size() : 0, 
                    user.getEmail(), labelId))
                .doOnError(error -> log.error("Failed to fetch threads for user: {}, labelId: {}", 
                    user.getEmail(), labelId, error))
                .defaultIfEmpty(new ListThreadResponse())
                .onErrorMap(e-> new RuntimeException("Failed to fetch messages",e));
    }

    // Fetch thread details by ID
    public Mono<ThreadDetailResponse> getThreadDetail(String id) {
        User user = getAuthenticatedUser();
        log.info("Fetching thread details for threadId: {} by user: {}", id, user.getEmail());
        
        String accessToken = getAccessToken();
        return gmailWebClient.get()
                .uri(uriBuilder ->
                    uriBuilder.path("/threads/{id}")
                            .queryParam("format", "full")
                            .build(id)
                )
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(ThreadDetail.class)
                .doOnSuccess(thread -> log.info("Successfully fetched thread details for threadId: {} by user: {}, messageCount: {}", 
                    id, user.getEmail(), thread != null && thread.getMessages() != null ? thread.getMessages().size() : 0))
                .doOnError(error -> log.error("Failed to fetch thread details for threadId: {} by user: {}", 
                    id, user.getEmail(), error))
                .map(this::parseListMessage);
    }
    public Mono<AttachmentResponse> getAttachment(String messageId,String attachmentId) {
        User user = getAuthenticatedUser();
        log.info("Fetching attachment {} for message {} by user: {}", attachmentId, messageId, user.getEmail());
        
        return gmailWebClient.get()
                .uri("/messages/{messageId}/attachments/{attachmentId}",messageId,attachmentId)
                .retrieve()
                .bodyToMono(AttachmentResponse.class)
                .doOnSuccess(attachment -> log.info("Successfully fetched attachment {} for message {} by user: {}", 
                    attachmentId, messageId, user.getEmail()))
                .doOnError(error -> log.error("Failed to fetch attachment {} for message {} by user: {}", 
                    attachmentId, messageId, user.getEmail(), error))
                .onErrorMap(e-> new RuntimeException("Failed to fetch attachment",e));
    }

    public Mono<GmailSendResponse> sendEmail(EmailSendRequest request) {
        User user = getAuthenticatedUser();
        log.info("Sending email from user: {} to: {}, subject: {}", 
                user.getEmail(), request.getTo(), request.getSubject());
        
        if(request.getInReplyToMessageId()!=null && !request.getInReplyToMessageId().isEmpty()){
            String subject = request.getSubject();
            String replySubject = subject.startsWith("Re:") ? subject : "Re: " + subject;
            request.setSubject(replySubject);
            log.debug("Email is a reply, modified subject to: {}", replySubject);
        }
        return Mono.fromCallable(() -> createMimeMessage(request))
                .doOnSuccess(mimeMessage -> log.debug("Successfully created MIME message for user: {}", user.getEmail()))
                .doOnError(error -> log.error("Failed to create MIME message for user: {}", user.getEmail(), error))
                .flatMap(email -> sendToGmailApi(email, request.getThreadId()))
                .doOnSuccess(response -> log.info("Successfully sent email from user: {} to: {}, messageId: {}", 
                        user.getEmail(), request.getTo(), response != null ? response.getId() : "unknown"))
                .doOnError(error -> log.error("Failed to send email from user: {} to: {}", 
                        user.getEmail(), request.getTo(), error));
    }
    public Mono<String> modifyMessageLabels(ModifyEmailRequest request) {
        User user = getAuthenticatedUser();
        log.info("Modifying labels for thread {} by user: {}, adding: {}, removing: {}", 
                request.getThreadId(), user.getEmail(), request.getAddLabelIds(), request.getRemoveLabelIds());
        
        Map<String, Object> payload = new HashMap<>();
        if (request.getAddLabelIds() != null) {
            payload.put("addLabelIds", request.getAddLabelIds());
        }
        if (request.getRemoveLabelIds() != null) {
            payload.put("removeLabelIds", request.getRemoveLabelIds());
        }
        String accessToken = getAccessToken();
        return gmailWebClient.post()
                .uri("/threads/{id}/modify", request.getThreadId())
                .headers(h -> h.setBearerAuth(accessToken))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Successfully modified labels for thread {} by user: {}", 
                        request.getThreadId(), user.getEmail()))
                .doOnError(error -> log.error("Failed to modify labels for thread {} by user: {}", 
                        request.getThreadId(), user.getEmail(), error))
                .map(response -> "Labels modified successfully")
                .onErrorMap(e -> new RuntimeException("Failed to modify message labels", e));
    }
    public Mono<Void> deleteMessage(String messageId)
    {
        User user = getAuthenticatedUser();
        log.info("Deleting message {} by user: {}", messageId, user.getEmail());
        
        String accessToken = getAccessToken();
        return gmailWebClient.delete()
                .uri("/messages/{id}",messageId)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(response -> log.info("Successfully deleted message {} by user: {}", 
                        messageId, user.getEmail()))
                .doOnError(error -> log.error("Failed to delete message {} by user: {}", 
                        messageId, user.getEmail(), error))
                .onErrorMap(e-> new RuntimeException("Failed to delete message",e));
    }
    public Mono<Void> deleteMail(String mailId)
    {
        User user = getAuthenticatedUser();
        log.info("Deleting mail thread {} by user: {}", mailId, user.getEmail());
        
        String accessToken = getAccessToken();
        return gmailWebClient.delete()
                .uri("/threads/{id}",mailId)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(response -> log.info("Successfully deleted mail thread {} by user: {}", 
                        mailId, user.getEmail()))
                .doOnError(error -> log.error("Failed to delete mail thread {} by user: {}", 
                        mailId, user.getEmail(), error))
                .onErrorMap(e-> new RuntimeException("Failed to delete message",e));
    }
    private Mono<GmailSendResponse> sendToGmailApi(MimeMessage email, String threadId) {
        User user = getAuthenticatedUser();
        log.debug("Preparing to send email to Gmail API for user: {}, threadId: {}", user.getEmail(), threadId);
        
        // 1. Đóng gói đoạn code Blocking vào Mono.fromCallable
        return Mono.fromCallable(() -> {
                    try {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        email.writeTo(buffer);
                        byte[] rawBytes = buffer.toByteArray();
                        String encodedEmail = Base64.getUrlEncoder().encodeToString(rawBytes);

                        Map<String, String> payload;
                        if (threadId != null) {
                            payload = Map.of("raw", encodedEmail, "threadId", threadId);
                            log.debug("Created email payload with threadId: {} for user: {}", threadId, user.getEmail());
                        } else {
                            payload = Map.of("raw", encodedEmail);
                            log.debug("Created email payload without threadId for user: {}", user.getEmail());
                        }
                        return payload;
                    } catch (Exception e) {
                        log.error("Failed to create email payload for user: {}", user.getEmail(), e);
                        // Ném lỗi ra để Mono xử lý
                        throw new RuntimeException("Failed to create email payload", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(payload -> {
                    String accessToken = getAccessToken();
                    return gmailWebClient.post()
                        .uri("/messages/send")
                        .headers(h -> h.setBearerAuth(accessToken))
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(GmailSendResponse.class)
                        .doOnSuccess(response -> log.debug("Gmail API call successful for user: {}, response: {}", 
                                user.getEmail(), response != null ? response.getId() : "null"))
                        .doOnError(error -> log.error("Gmail API call failed for user: {}", user.getEmail(), error));
                });
    }

    private ThreadDetailResponse parseListMessage(ThreadDetail threadDetail)
    {
        User user = getAuthenticatedUser();
        log.debug("Parsing thread detail for threadId: {} by user: {}", 
                threadDetail != null ? threadDetail.getId() : "null", user.getEmail());
        
        ThreadDetailResponse threadDetailResponse = ThreadDetailResponse.builder()
                .id(threadDetail.getId())
                .snippet(threadDetail.getSnippet())
                .messages(new ArrayList<>())
                .build();
        if(threadDetail.getMessages()!=null)
        {
            log.debug("Parsing {} messages for thread {} by user: {}", 
                    threadDetail.getMessages().size(), threadDetail.getId(), user.getEmail());
            
            threadDetail.getMessages().forEach(message -> {
                MessageDetailResponse messageDetailResponse = parseMessage(message);
                threadDetailResponse.getMessages().add(messageDetailResponse);
            });
            
            log.debug("Successfully parsed thread detail for threadId: {} with {} messages by user: {}", 
                    threadDetail.getId(), threadDetailResponse.getMessages().size(), user.getEmail());
        } else {
            log.warn("No messages found in thread {} for user: {}", threadDetail.getId(), user.getEmail());
        }
        return threadDetailResponse;
    }

    private MessageDetailResponse parseMessage(Message message)
    {
        MessageDetailResponse messageDetailResponse = MessageDetailResponse.builder()
                .id(message.getId())
                .threadId(message.getThreadId())
                .snippet(message.getSnippet())
                .attachments(new ArrayList<>())
                .build();
        if(message.getPayload()!=null)
        {
            List<MessagePartHeader> headers = message.getPayload().getHeaders();
            messageDetailResponse.setMessageId(getHeader(headers,"Message-ID"));
            messageDetailResponse.setFrom(getHeader(headers, "From"));
            messageDetailResponse.setTo(getHeader(headers, "To"));
            messageDetailResponse.setSubject(getHeader(headers, "Subject"));
            String dateStr = getHeader(headers, "Date");
            if(dateStr!=null && !dateStr.isEmpty())
            {
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

    private void traverseParts (MessagePart part, MessageDetailResponse messageDetailResponse){
        if (part == null) return;
        if(part.getBody()!=null&&part.getBody().getData()!=null)
        {
            String decoded = decodeBase64UrlSafe(part.getBody().getData());
            switch (part.getMimeType().toLowerCase()){
                case "text/plain":
                    if(messageDetailResponse.getTextBody()==null)
                    {
                        messageDetailResponse.setTextBody(decoded);
                    }
                    break;
                case "text/html":
                    if(messageDetailResponse.getHtmlBody()==null)
                    {
                        messageDetailResponse.setHtmlBody(decoded);
                    }
                    break;
            }
        }
        if (part.getFilename() != null && !part.getFilename().isBlank()) {
            messageDetailResponse.getAttachments().add(new Attachment(
                    part.getFilename(),
                    part.getMimeType(),
                    part.getBody() != null ? part.getBody().getAttachmentId() : null
            ));
        }
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                traverseParts(child, messageDetailResponse);
            }
        }
    }
    private String getHeader(List<MessagePartHeader> headers,String name)
    {
        if(headers == null) return null;
        for (MessagePartHeader h : headers) {
            if (name.equalsIgnoreCase(h.getName())) return h.getValue();
        }
        return null;
    }
    private String decodeBase64UrlSafe(String data) {
        if (data == null) return null;
        byte[] decoded = Base64.getUrlDecoder().decode(data);
        return new String(decoded, StandardCharsets.UTF_8);
    }
    private MimeMessage createMimeMessage(EmailSendRequest request) throws MessagingException, IOException {
        Session session = Session.getDefaultInstance(new Properties(), null);
        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress("me"));
        if(request.getTo()!=null && !request.getTo().isEmpty())
        {
            email.addRecipients(jakarta.mail.Message.RecipientType.TO,InternetAddress.parse(request.getTo()));
        }
        if(request.getBcc()!=null && !request.getBcc().isEmpty())
        {
            email.addRecipients(jakarta.mail.Message.RecipientType.BCC,InternetAddress.parse(request.getBcc()));
        }
        if(request.getCc()!=null && !request.getCc().isEmpty())
        {
            email.addRecipients(jakarta.mail.Message.RecipientType.CC,InternetAddress.parse(request.getCc()));
        }
        email.setSubject(request.getSubject(),"UTF-8");
        if(request.getInReplyToMessageId()!=null&& !request.getInReplyToMessageId().isEmpty())
        {
            email.setHeader("In-Reply-To",request.getInReplyToMessageId());
            email.setHeader("References",request.getInReplyToMessageId());
        }

        MimeMultipart rootMultipart = new MimeMultipart("mixed");
        MimeBodyPart contentBodyPart = getMimeBodyPart(request);
        rootMultipart.addBodyPart(contentBodyPart);
        if(request.getAttachment() != null)
        {
            for(MultipartFile file:request.getAttachment())
            {
                if(file.isEmpty()) continue;
                MimeBodyPart attachmentPart = new MimeBodyPart();
                String fileName = file.getOriginalFilename();
                attachmentPart.setFileName(fileName);
                attachmentPart.setContent(file.getBytes(), file.getContentType());
                rootMultipart.addBodyPart(attachmentPart);
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
        String plainText = request.isHtml() ? content.replaceAll("\\<.*?\\>", "") : content;
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
}
