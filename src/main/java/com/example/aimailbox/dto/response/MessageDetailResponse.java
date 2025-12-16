package com.example.aimailbox.dto.response;

import com.example.aimailbox.dto.response.mail.Attachment;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageDetailResponse {
    String id;
    String messageId;
    String threadId;
    String from;
    String to;
    String cc;
    String bcc;
    String subject;
    String date;
    String snippet;
    String textBody;
    String htmlBody;
    List<String> labelIds;
    List<Attachment> attachments = new ArrayList<>();
}
