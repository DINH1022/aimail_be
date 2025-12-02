package com.example.aimailbox.dto.response.mail;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class MessagePart {
    String mimeType;
    String filename;
    MessagePartBody body;
    List<MessagePartHeader> headers;
    List<MessagePart> parts;
}
