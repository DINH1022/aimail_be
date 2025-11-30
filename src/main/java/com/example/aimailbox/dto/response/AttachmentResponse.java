package com.example.aimailbox.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class AttachmentResponse {
    String attachmentId;
    Long size;
    String data;
}
