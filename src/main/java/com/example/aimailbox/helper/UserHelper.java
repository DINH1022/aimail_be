package com.example.aimailbox.helper;

import com.example.aimailbox.model.User;
import com.example.aimailbox.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class UserHelper {
    private final UserRepository userRepository;
    public Mono<User> getCurrentUser() {
        return Mono.fromCallable(() ->
                        SecurityContextHolder.getContext().getAuthentication()
                )
                .flatMap(this::resolveUserFromAuthentication);
    }

    private Mono<User> resolveUserFromAuthentication(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Mono.error(new RuntimeException("User not authenticated"));
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof User user) {
            return Mono.just(user);
        }

        String email = extractEmail(principal);

        return Mono.fromCallable(() -> userRepository.findByEmail(email))
                .subscribeOn(Schedulers.boundedElastic()) // vì JPA là blocking
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(
                        Mono.error(new RuntimeException("User not found: " + email))
                );
    }
    private String extractEmail(Object principal) {
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        if (principal instanceof String email) {
            return email;
        }

        throw new RuntimeException(
                "Unsupported principal type: " + principal.getClass()
        );
    }

    public User getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new RuntimeException("User not authenticated");
        }
        Object principal = authentication.getPrincipal();

        if (principal instanceof User) {
            return (User) principal;
        } else {
            String email = authentication.getName();
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        }
    }
}
