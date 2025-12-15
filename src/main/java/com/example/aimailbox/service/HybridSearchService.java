package com.example.aimailbox.service;

import com.example.aimailbox.dto.response.ThreadDetailResponse;
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

    public Mono<List<ThreadDetailResponse>> searchFuzzyEmails(String query) {
        // 1. Sử dụng Mono.fromCallable để lấy SecurityContext từ ThreadLocal (Chuẩn MVC)
        return Mono.fromCallable(() -> SecurityContextHolder.getContext().getAuthentication())
                .flatMap(auth -> {
                    // Kiểm tra đăng nhập
                    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                        return Mono.error(new RuntimeException("Unauthorized: No authentication found"));
                    }

                    String email = null;
                    // Lấy email từ các loại Principal khác nhau
                    if (auth.getPrincipal() instanceof User user) {
                        email = user.getEmail();
                    } else if (auth.getPrincipal() instanceof String) {
                        email = (String) auth.getPrincipal();
                    } else if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                        email = userDetails.getUsername();
                    }

                    if (email == null) {
                        return Mono.error(new RuntimeException("Unauthorized: Email not found"));
                    }
                    return Mono.just(email);
                })
                .map(email -> {
                    // 2. Chỉ gọi Search trong Cache
                    // Nếu cache rỗng -> Trả về List rỗng luôn (Không gọi Gmail API nữa)
                    return emailCacheService.searchInCache(query, email);
                });
    }

    private final UserRepository userRepository; // Nhớ Inject cái này

    public Mono<Void> refreshData() {
        return Mono.fromCallable(() -> SecurityContextHolder.getContext().getAuthentication())
                .flatMap(auth -> {
                    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                        return Mono.error(new RuntimeException("User not authenticated"));
                    }

                    // Principal thường là UserDetails hoặc String (email) tùy vào JwtFilter của bạn
                    String email;
                    Object principal = auth.getPrincipal();

                    if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                        email = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                    } else if (principal instanceof String) {
                        email = (String) principal;
                    } else if (principal instanceof User) {
                        // Trường hợp hiếm: Filter của bạn đã lưu entity User vào context
                        return Mono.just((User) principal);
                    } else {
                        return Mono.error(new RuntimeException("Unknown principal type: " + principal.getClass()));
                    }

                    // Query DB để lấy Entity User đầy đủ (để có access token)
                    return Mono.justOrEmpty(userRepository.findByEmail(email))
                            .switchIfEmpty(Mono.error(new RuntimeException("User not found in database: " + email)));
                })
                // Gọi service sync
                .flatMap(user -> emailCacheService.syncRecentMails(user));
    }
}
