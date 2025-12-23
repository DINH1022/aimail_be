package com.example.aimailbox.service;

import com.example.aimailbox.helper.UserHelper;
import com.example.aimailbox.model.Email;
import com.example.aimailbox.model.User;
import com.example.aimailbox.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SematicSearchService {
    private final EmailRepository emailRepository;
    private final ProxyMailService proxyMailService;
    private final EmailService emailService;
    private final UserHelper userHelper;

    public Mono<Void> syncEmails() {
        return userHelper.getCurrentUser()
                .doOnNext(this::syncEmailFromGmailToDB)
                .then();
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
            batchSize = 2;
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
}
