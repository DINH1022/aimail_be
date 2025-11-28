package com.example.aimailbox.controller;

import com.example.aimailbox.dto.response.MessageDetailResponse;
import com.example.aimailbox.dto.response.ThreadDetailResponse;
import com.example.aimailbox.service.ProxyMailService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/emails")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class EmailController {
    ProxyMailService proxyMailService;
    @GetMapping("/{id}")
    public Mono<ThreadDetailResponse> getEmailDetail (@PathVariable String id) {
        return proxyMailService.getThreadDetail(id);
    }
}
