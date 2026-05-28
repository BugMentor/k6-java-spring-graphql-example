package com.enterprise.payment.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {}

    public User(UUID id, String email, String fullName, String status) {
        validate(email, fullName);
        this.id = id != null ? id : UUID.randomUUID();
        this.email = email;
        this.fullName = fullName;
        this.status = status != null ? status : "ACTIVE";
        this.createdAt = Instant.now();
    }

    private void validate(String email, String fullName) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be empty");
        }
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getStatus() { return status; }
}
