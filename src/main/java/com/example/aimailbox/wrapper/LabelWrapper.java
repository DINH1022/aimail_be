package com.example.aimailbox.wrapper;

import com.example.aimailbox.dto.response.LabelResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults (level =  AccessLevel.PRIVATE)
public class LabelWrapper {
    List<LabelResponse> labels;
}
