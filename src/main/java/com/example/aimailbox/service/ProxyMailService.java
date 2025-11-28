package com.example.aimailbox.service;

import com.example.aimailbox.dto.response.*;
import com.example.aimailbox.dto.response.mail.*;
import com.example.aimailbox.wrapper.LabelWrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@FieldDefaults(makeFinal = true,level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class ProxyMailService {
    // In a real application, the token would be retrieved from the authenticated user's session or security context
    String token ="";
    WebClient gmailWebClient;
    ObjectMapper objectMapper;
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
        System.out.println(message);
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
    private Message decodeNestedMessage(String base64Data) {
        if (base64Data == null) return null;
        byte[] decoded = Base64.getUrlDecoder().decode(base64Data);
        try {
            return objectMapper.readValue(decoded, Message.class);
        } catch (Exception e) {
            return null;
        }
    }
}
