package com.example.aimailbox.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "Label type must not be null")
    boolean systemLabel;
}
