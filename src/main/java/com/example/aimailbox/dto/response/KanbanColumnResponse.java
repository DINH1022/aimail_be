package com.example.aimailbox.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KanbanColumnResponse {
    Long id;
    String name;
}
