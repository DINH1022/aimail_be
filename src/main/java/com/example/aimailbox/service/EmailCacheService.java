package com.example.aimailbox.service;

import com.example.aimailbox.dto.response.MessageDetailResponse;
import com.example.aimailbox.dto.response.ThreadDetailResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class EmailCacheService {
    ProxyMailService proxyMailService;
    final List<ThreadDetailResponse> cachedThreads = new CopyOnWriteArrayList<>();
    public Mono<Void> syncRecentMails(){
        return proxyMailService.getListThreads(50,null,null,null,false)
                .flatMapMany(responses -> Flux.fromIterable(responses.getThreads()))
                .flatMap(shortThread-> proxyMailService.getThreadDetail(shortThread.getId()))
                .collectList()
                .doOnNext(details->{
                    cachedThreads.clear();
                    cachedThreads.addAll(details);
                })
                .then();
    }
    public List<ThreadDetailResponse> searchInCache(String query){
        if(query == null||query.isBlank()){
            return cachedThreads;
        }
        String lowerQuery = query.toLowerCase();
        return cachedThreads.stream()
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
                .filter(wrapper->wrapper.score>60)
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
