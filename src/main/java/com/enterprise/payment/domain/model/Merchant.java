package com.enterprise.payment.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Merchant() {}

    public Merchant(UUID id, String name, String apiKey) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name;
        this.apiKey = apiKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getApiKey() { return apiKey; }
}
