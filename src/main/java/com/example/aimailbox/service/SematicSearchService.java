package com.example.aimailbox.service;

import com.example.aimailbox.model.Email;
import com.example.aimailbox.model.User;
import com.example.aimailbox.repository.EmailRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class SematicSearchService {
    private final EmailRepository emailRepository;
    private final ProxyMailService proxyMailService;
    private final EmailService emailService;

    public void syncEmailFromGmailToDB(User user)
    {
        String query;
        int initialMaxResults;
        Email lastEmailReceived = emailRepository.findFirstByUserOrderByReceivedAtDesc(user);
        Instant lastReceived = lastEmailReceived.getReceivedAt();
        if (lastReceived == null) {
            query = "";
            initialMaxResults = 100;
        } else {
            query = "after:" + lastReceived.getEpochSecond();
            initialMaxResults = 50;
        }
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        fetchPageRecursive(user, query, initialMaxResults, null)
                .contextWrite(Context.of(Authentication.class, auth))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> log.error("Sync FAILED for user {}: ", user.getEmail(), error),
                        () -> log.info("Sync COMPLETED successfully for user {}", user.getEmail())
                );
    }

    private Flux<Void> fetchPageRecursive(User user, String query, int maxResults, String pageToken) {
        return proxyMailService.getListThreads(maxResults, pageToken, query, null, false)
                .flatMapMany(response -> {
                    if (response == null || response.getThreads() == null || response.getThreads().isEmpty()) {
                        return Flux.empty();
                    }
                    Flux<Void> currentBatchProcessing = Flux.fromIterable(response.getThreads())
                            .flatMap(shortThread ->
                                            proxyMailService.getThreadDetail(shortThread.getId())
                                                    .doOnNext(detail -> {
                                                        try {
                                                            emailService.saveEmailToDatabase(user, detail);
                                                        } catch (Exception e) {
                                                            log.error("Failed to save thread {}: {}", detail.getId(), e.getMessage());
                                                        }
                                                    })
                                                    .onErrorResume(e -> {
                                                        log.warn("Error fetching thread details {}: {}", shortThread.getId(), e.getMessage());
                                                        return Mono.empty();
                                                    }),
                                    5
                            )
                            .then()
                            .flux();

                    String nextPage = response.getNextPageToken();
                    if (nextPage != null && !nextPage.isBlank()) {
                        return currentBatchProcessing.concatWith(
                                fetchPageRecursive(user, query, maxResults, nextPage)
                        );
                    } else {
                        return currentBatchProcessing;
                    }
                });
    }
}
