package com.example.aimailbox.service;

import com.example.aimailbox.dto.response.ThreadDetailResponse;
import com.example.aimailbox.helper.UserHelper;
import com.example.aimailbox.model.User;
import com.example.aimailbox.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
@Slf4j

@Service
@FieldDefaults(level = AccessLevel.PRIVATE,makeFinal = true)
@RequiredArgsConstructor
public class HybridSearchService {
    EmailCacheService emailCacheService;
    UserHelper userHelper;
    public Mono<List<ThreadDetailResponse>> searchFuzzyEmails(String query) {
        return userHelper.getCurrentUser()
                .map(user ->
                        emailCacheService.searchInCache(query,user.getEmail()));
    }
    public Mono<Void> refreshData() {
        return userHelper.getCurrentUser()
                .flatMap(emailCacheService::syncRecentMails);
    }
}
