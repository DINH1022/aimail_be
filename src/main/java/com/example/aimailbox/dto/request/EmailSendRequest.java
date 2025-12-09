package com.example.aimailbox.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class EmailSendRequest {
    String to;
    String cc;
    String bcc;
    String subject;
    String content;
    boolean isHtml;
    MultipartFile [] attachment;
    String threadId;
    String inReplyToMessageId;
}
