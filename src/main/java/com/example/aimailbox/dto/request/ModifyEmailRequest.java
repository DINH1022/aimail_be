package com.example.aimailbox.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class ModifyEmailRequest {
    String threadId;
    List<String> addLabelIds;
    List<String> removeLabelIds;
}
