package com.example.aimailbox.service;

import com.example.aimailbox.dto.request.EmailSendRequest;
import com.example.aimailbox.dto.response.*;
import com.example.aimailbox.dto.response.mail.*;
import com.example.aimailbox.wrapper.LabelWrapper;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
@FieldDefaults(makeFinal = true,level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class ProxyMailService {
    // In a real application, the token would be retrieved from the authenticated user's session or security context
    String token ="";
    WebClient gmailWebClient;
    // Fetch all labels
    public Mono<List<LabelResponse>> getAllLabels() {
        return gmailWebClient.get()
                .uri("/labels")
                .headers(header ->header.setBearerAuth(token))
                .retrieve()
                .bodyToMono(LabelWrapper.class)
                .map(LabelWrapper::getLabels)
                .defaultIfEmpty(List.of())
                .onErrorMap(e-> new RuntimeException("Failed to fetch labels",e));
    }
    // Fetch label details by ID
    public Mono<LabelDetailResponse>  getLabel(String id) {
        return gmailWebClient.get()
                .uri("/labels/{id}",id)
                .headers(header ->header.setBearerAuth(token))
                .retrieve()
                .bodyToMono(LabelDetailResponse.class)
                .defaultIfEmpty(new LabelDetailResponse())
                .onErrorMap(e-> new RuntimeException("Failed to fetch label details",e));
    }
    // Fetch list of threads with optional parameters
    public Mono<ListThreadResponse> getListThreads(Integer maxResults
            , String pageToken
            , String query
            , String labelId
            , Boolean includeSpamTrash) {
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
                .headers(header ->header.setBearerAuth(token))
                .retrieve()
                .bodyToMono(ListThreadResponse.class)
                .defaultIfEmpty(new ListThreadResponse())
                .onErrorMap(e-> new RuntimeException("Failed to fetch messages",e));
    }

    // Fetch thread details by ID
    public Mono<ThreadDetailResponse> getThreadDetail(String id) {
        return gmailWebClient.get()
                .uri(uriBuilder ->
                    uriBuilder.path("/threads/{id}")
                            .queryParam("format", "full")
                            .build(id)
                )
                .headers(header ->header.setBearerAuth(token))
                .retrieve()
                .bodyToMono(ThreadDetail.class)
                .map(this::parseListMessage);
    }
    public Mono<AttachmentResponse> getAttachment(String messageId,String attachmentId) {
        return gmailWebClient.get()
                .uri("/messages/{messageId}/attachments/{attachmentId}",messageId,attachmentId)
                .headers(header ->header.setBearerAuth(token))
                .retrieve()
                .bodyToMono(AttachmentResponse.class)
                .onErrorMap(e-> new RuntimeException("Failed to fetch attachment",e));
    }

    public Mono<GmailSendResponse> sendEmail(EmailSendRequest request) {
        if(request.getInReplyToMessageId()!=null && !request.getInReplyToMessageId().isEmpty()){
            String subject = request.getSubject();
            String replySubject = subject.startsWith("Re:") ? subject : "Re: " + subject;
            request.setSubject(replySubject);
        }
        return Mono.fromCallable(() -> createMimeMessage(request))
                .flatMap(email -> sendToGmailApi(email, request.getThreadId()));
    }
    private Mono<GmailSendResponse> sendToGmailApi(MimeMessage email, String threadId) {
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
                        } else {
                            payload = Map.of("raw", encodedEmail);
                        }
                        return payload;
                    } catch (Exception e) {
                        // Ném lỗi ra để Mono xử lý
                        throw new RuntimeException("Failed to create email payload", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(payload -> gmailWebClient.post()
                        .uri("/messages/send")
                        .headers(header -> header.setBearerAuth(token))
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(GmailSendResponse.class)
                );
    }

    private ThreadDetailResponse parseListMessage(ThreadDetail threadDetail)
    {
        ThreadDetailResponse threadDetailResponse = ThreadDetailResponse.builder()
                .id(threadDetail.getId())
                .snippet(threadDetail.getSnippet())
                .messages(new ArrayList<>())
                .build();
        if(threadDetail.getMessages()!=null)
        {
            for (Message message : threadDetail.getMessages()) {
                MessageDetailResponse messageDetailResponse = parseMessage(message);
                threadDetailResponse.getMessages().add(messageDetailResponse);
            }
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
