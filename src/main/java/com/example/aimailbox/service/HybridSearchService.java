package com.example.aimailbox.service;

import com.example.aimailbox.dto.response.ThreadDetailResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class HybridSearchService {
    EmailCacheService emailCacheService;
    ProxyMailService proxyMailService;

    public Mono<List<ThreadDetailResponse>> searchFuzzyEmails(String query) {
        List<ThreadDetailResponse> cachedEmails = emailCacheService.searchInCache(query);
        if(!cachedEmails.isEmpty()){
            return Mono.just(cachedEmails);
        }
        return proxyMailService.getListThreads(20,null,query,null,false)
                .flatMap(listResponse->{
                    if(listResponse==null||listResponse.getThreads().isEmpty()){
                        return Mono.just(List.of());
                    }
                    return Flux.fromIterable(listResponse.getThreads())
                            .flatMap(shortThread-> proxyMailService.getThreadDetail(shortThread.getId()))
                            .collectList();
                });
    }
    public Mono<Void> refreshData() {
        return emailCacheService.syncRecentMails();
    }
}
