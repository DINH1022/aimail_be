package com.example.aimailbox.dto.response.mail;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class Attachment {
   String filename;
   String mimeType;
   String attachmentId;
}
