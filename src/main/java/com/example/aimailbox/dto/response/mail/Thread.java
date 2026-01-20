package com.example.aimailbox.dto.response.mail;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class Thread {
    String id;
    String snippet;
    List<String> labelIds;
}

