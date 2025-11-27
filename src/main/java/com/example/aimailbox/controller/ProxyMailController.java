package com.example.aimailbox.controller;

import com.example.aimailbox.dto.response.LabelDetailResponse;
import com.example.aimailbox.dto.response.LabelResponse;
import com.example.aimailbox.service.ProxyMailService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/mailboxes")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ProxyMailController {
    ProxyMailService proxyMailService;

    @GetMapping("")
    public Mono<List<LabelResponse>> getAllLabels() {
        return proxyMailService.getAllLabels();
    }
    @GetMapping("/{id}")
    public Mono<LabelDetailResponse> getAllLabels(@PathVariable String id) {
        return proxyMailService.getLabel(id);
    }
}
