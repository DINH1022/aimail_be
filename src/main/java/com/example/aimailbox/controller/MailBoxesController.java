package com.example.aimailbox.controller;

import com.example.aimailbox.dto.request.LabelCreationRequest;
import com.example.aimailbox.dto.request.LabelUpdateRequest;
import com.example.aimailbox.dto.response.LabelDetailResponse;
import com.example.aimailbox.dto.response.LabelResponse;
import com.example.aimailbox.dto.response.ListThreadResponse;
import com.example.aimailbox.service.ProxyMailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/mailboxes")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class MailBoxesController {
    ProxyMailService proxyMailService;

    @GetMapping("")
    public Mono<List<LabelResponse>> getAllLabels() {
        return proxyMailService.getAllLabels();
    }

    @GetMapping("/{id}")
    public Mono<LabelDetailResponse> getAllLabels(@PathVariable String id) {
        return proxyMailService.getLabel(id);
    }

    @GetMapping("/{id}/emails")
    public Mono<ListThreadResponse> getListThreads(
            @PathVariable String id,
            @RequestParam(required = false) Integer maxResults,
            @RequestParam(required = false) String pageToken,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "false") Boolean includeSpamTrash) {
        return proxyMailService.getListThreadsWithSnoozeFilter(maxResults, pageToken, query, id, includeSpamTrash);
    }
    @PostMapping
    public Mono<LabelDetailResponse> createLabel(@Valid @RequestBody LabelCreationRequest request) {
        return proxyMailService.createLabel(request);
    }
    @PatchMapping
    public Mono<LabelDetailResponse> updateLabel(@RequestParam String id, @Valid @RequestBody LabelUpdateRequest request) {
        return proxyMailService.updateLabel(request, id);
    }
    @DeleteMapping("/{id}")
    public Mono<Void> deleteLabel(@PathVariable String id) {
        return proxyMailService.deleteLabel(id);
    }

}