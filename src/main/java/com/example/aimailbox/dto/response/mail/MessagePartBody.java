package com.example.aimailbox.dto.response.mail;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class MessagePartBody {
    String data;          // base64 url-safe
    String attachmentId;
}
