package com.example.aimailbox.service;

import com.example.aimailbox.dto.response.ListThreadResponse;
import com.example.aimailbox.dto.response.MessageDetailResponse;
import com.example.aimailbox.dto.response.ThreadDetailResponse;
import com.example.aimailbox.model.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE,makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class EmailCacheService {
    ProxyMailService proxyMailService;
    Map<String, List<ThreadDetailResponse>> userCache = new ConcurrentHashMap<>();
    public Mono<Void> syncRecentMails(User user) {
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        return proxyMailService.getListThreads(100, null, null, null, false)
                .flatMapMany(response -> {
                    if (response == null || response.getThreads() == null || response.getThreads().isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(response.getThreads());
                })
                .parallel()
                .runOn(Schedulers.boundedElastic())
                .flatMap(shortThread -> proxyMailService.getThreadDetail(shortThread.getId())
                        .onErrorResume(e -> {
                            return Mono.empty();
                        }))
                .sequential()
                .collectList()
                .doOnNext(details -> {
                    if (details.isEmpty()) {
                        log.warn("SYNC COMPLETED but result list is EMPTY. Check errors above.");
                    } else {
                        userCache.put(user.getEmail(), details);
                    }
                })
                .doOnError(e -> log.error("SYNC FAILED with fatal error: ", e)) // Bắt lỗi nếu getListThreads chết
                .then()
                .contextWrite(Context.of(Authentication.class, auth));
    }
    public List<ThreadDetailResponse> searchInCache(String query,String userEmail) {
        List<ThreadDetailResponse> currentData = userCache.getOrDefault(userEmail, List.of());
        if(query == null || query.isBlank()){
            return List.of();
        }
        String lowerQuery = query.toLowerCase();
        return currentData.stream()
                .map(thread-> {
                    int maxScoreOfThread = 0;
                    for (MessageDetailResponse message : thread.getMessages()) {
                        String subject = message.getSubject() != null ? message.getSubject() : "";
                        int subjectScore = FuzzySearch.weightedRatio(lowerQuery, subject);
                        String sender = message.getFrom() != null ? message.getFrom() : "";
                        int senderScore = FuzzySearch.weightedRatio(lowerQuery, sender);
                        String snippet = message.getSnippet() != null ? message.getSnippet() : "";
                        int snippetScore = FuzzySearch.weightedRatio(lowerQuery, snippet);
                        int messageMaxScore = Math.max(subjectScore, Math.max(senderScore, snippetScore));
                        if (messageMaxScore > maxScoreOfThread) {
                            maxScoreOfThread = messageMaxScore;
                        }
                    }
                    return new ThreadScoreWrapper(thread, maxScoreOfThread);
                })
                .filter(wrapper -> wrapper.score > 60)
                .sorted(Comparator.comparingInt(ThreadScoreWrapper::getScore).reversed())
                .map(ThreadScoreWrapper::getThread)
                .toList();
    }

    @Data
    @AllArgsConstructor
    private static class ThreadScoreWrapper {
        ThreadDetailResponse thread;
        int score;
    }
}
