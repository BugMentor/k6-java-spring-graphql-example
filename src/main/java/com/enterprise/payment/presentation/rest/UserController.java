package com.enterprise.payment.presentation.rest;

import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserJpaRepository userJpaRepository;

    public UserController(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @PostMapping
    @WithSpan("create-user")
    public ResponseEntity<User> createUser(
            @SpanAttribute("user.email") @RequestBody CreateUserRequest request) {
        try {
            User user = new User(
                    UUID.randomUUID(),
                    request.email(),
                    request.fullName(),
                    request.status() != null ? request.status() : "ACTIVE"
            );
            User saved = userJpaRepository.save(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    @WithSpan("get-user")
    public ResponseEntity<User> getUser(@PathVariable UUID id) {
        return userJpaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @WithSpan("list-users")
    public ResponseEntity<List<User>> listUsers() {
        return ResponseEntity.ok(userJpaRepository.findAll());
    }

    @DeleteMapping("/{id}")
    @WithSpan("delete-user")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        if (userJpaRepository.existsById(id)) {
            userJpaRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    public record CreateUserRequest(String email, String fullName, String status) {}
}