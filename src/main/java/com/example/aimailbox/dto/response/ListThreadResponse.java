package com.example.aimailbox.dto.response;

import com.example.aimailbox.dto.response.mail.Thread;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class ListThreadResponse {
    List<Thread> threads;
    String nextPageToken;
    String resultSizeEstimate;
}
