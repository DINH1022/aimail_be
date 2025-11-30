package com.example.aimailbox.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class ThreadDetailResponse {
    String id;
    String snippet;
    List<MessageDetailResponse> messages;
}
