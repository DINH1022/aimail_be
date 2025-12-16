package com.example.aimailbox.dto.response.mail;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class Message {
    private String id;
    private String threadId;
    private java.util.List<String> labelIds;
    private String snippet;
    private MessagePart payload;
}
