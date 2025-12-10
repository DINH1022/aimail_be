package com.example.aimailbox.dto.request;

import com.example.aimailbox.model.EmailStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmailStatusRequest {
    
    @NotNull(message = "Status is required")
    private EmailStatus status;
}
