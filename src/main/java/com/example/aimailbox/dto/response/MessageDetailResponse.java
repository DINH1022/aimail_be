package com.example.aimailbox.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class MessageDetailResponse {
    String id;
    String threadId;
    String from;
    String to;
    String subject;
    String date;
    String snippet;
    String textBody;
    String htmlBody;
    List<AttachmentResponse> attachments = new ArrayList<>();
}
