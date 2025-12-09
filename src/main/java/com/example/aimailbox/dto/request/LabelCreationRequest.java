package com.example.aimailbox.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level =  AccessLevel.PRIVATE)
public class LabelCreationRequest {
    @NotBlank(message = "Label name must not be blank")
    String name;
}
