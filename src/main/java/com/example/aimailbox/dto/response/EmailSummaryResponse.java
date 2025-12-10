package com.example.aimailbox.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailSummaryResponse {
    String messageId;
    String subject;
    String from;
    String to;
    String date;
    String oneLineSubject;
    List<String> bullets;
    String summary;

    public EmailSummaryResponse(String summary) {
        this.summary = summary;
    }
}
