package com.example.aimailbox.service;

import com.example.aimailbox.dto.response.EmailResponse;
import com.example.aimailbox.helper.UserHelper;
import com.example.aimailbox.model.Email;
import com.example.aimailbox.model.User;
import com.example.aimailbox.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SematicSearchService {
    private final EmailRepository emailRepository;
    private final ProxyMailService proxyMailService;
    private final EmailService emailService;
    private final UserHelper userHelper;
    private final EmbeddingService embeddingService;
    public Mono<Void> syncEmails() {
        return userHelper.getCurrentUser()
                .doOnNext(this::syncEmailFromGmailToDB)
                .then();
    }

    public List<EmailResponse> searchSematic( String query) {
        User user = userHelper.getUser();
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        try {
            float[] queryVector = embeddingService.getEmbedding(query).block();
            if (queryVector == null || queryVector.length == 0) {
                log.warn("Embedding service returned empty vector");
                return Collections.emptyList();
            }
            List<Email> emails = emailRepository.searchBySemantic(user.getId(), queryVector,1.2);
            return emails.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error during semantic search", e);
            return Collections.emptyList();
        }
    }
    private void syncEmailFromGmailToDB(User user) {
        String query;
        int batchSize;
        boolean fetchAll;
        Instant lastReceived = emailRepository
                .findFirstByUserOrderByReceivedAtDesc(user)
                .map(Email::getReceivedAt)
                .orElse(null);

        if (lastReceived == null) {
            query = "";
            batchSize = 100;
            fetchAll = false;
        } else {
            query = "after:" + lastReceived.getEpochSecond();
            batchSize = 50;
            fetchAll = true;
        }

        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        fetchPageRecursive(user, query, batchSize, null, fetchAll)
                .contextWrite(Context.of(Authentication.class, auth))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> log.error("Sync FAILED for user {}: ", user.getEmail(), error),
                        () -> log.info("Sync COMPLETED for user {}. Mode: {}", user.getEmail(), fetchAll ? "FULL SYNC" : "INITIAL BATCH")
                );
    }

    private Flux<Void> fetchPageRecursive(User user, String query, int maxResults, String pageToken, boolean fetchAll) {
        return proxyMailService.getListThreads(maxResults, pageToken, query, null, false)
                .flatMapMany(response -> {
                    if (response == null || response.getThreads() == null || response.getThreads().isEmpty()) {
                        return Flux.empty();
                    }

                    Flux<Void> currentBatchProcessing = Flux.fromIterable(response.getThreads())
                            .flatMap(shortThread ->
                                            proxyMailService.getThreadDetail(shortThread.getId())
                                                    .flatMap(detail ->
                                                            Mono.fromRunnable(() -> {
                                                                        try {
                                                                            emailService.saveThreadToDatabase(user, detail);
                                                                        } catch (Exception e) {
                                                                            log.error("Failed to save thread {}: {}", detail.getId(), e.getMessage());
                                                                        }
                                                                    })
                                                                    .subscribeOn(Schedulers.boundedElastic())
                                                                    .then()
                                                    )
                                                    .onErrorResume(e -> {
                                                        log.warn("Error fetching thread details {}: {}", shortThread.getId(), e.getMessage());
                                                        return Mono.empty();
                                                    }),
                                    5 // Concurrency Limit
                            );

                    String nextPage = response.getNextPageToken();
                    if (nextPage != null && !nextPage.isBlank() && fetchAll) {
                        return currentBatchProcessing.concatWith(
                                fetchPageRecursive(user, query, maxResults, nextPage, true) // Tiếp tục đệ quy
                        );
                    } else {
                        return currentBatchProcessing;
                    }
                });
    }

    private EmailResponse mapToResponse(Email email) {
        return EmailResponse.builder()
                .id(email.getId())
                .threadId(email.getThreadId())
                .from(email.getFrom())
                .to(email.getTo())
                .subject(email.getSubject())
                .snippet(email.getSnippet())
                .body(email.getBody())
                .summary(email.getSummary())
                .status(email.getStatus())
                .snoozedUntil(email.getSnoozedUntil())
                .isStarred(email.getIsStarred())
                .receivedAt(email.getReceivedAt())
                .hasAttachments(email.getHasAttachments() != null && email.getHasAttachments())
                .isRead(email.getIsRead() != null && email.getIsRead())
                .build();
    }
}
