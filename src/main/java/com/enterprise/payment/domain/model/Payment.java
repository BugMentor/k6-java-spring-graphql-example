package com.enterprise.payment.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Payment() {}

    public Payment(UUID id, User user, Merchant merchant, Wallet wallet, BigDecimal amount, String type, String status, Instant createdAt, Long version) {
        this.id = id != null ? id : UUID.randomUUID();
        this.user = user;
        this.merchant = merchant;
        this.wallet = wallet;
        this.amount = amount;
        this.type = type;
        this.status = status != null ? status : "PENDING";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.version = version;
    }

    public Payment refund() {
        if (!"SUCCESS".equals(this.status)) {
            throw new IllegalStateException("Only SUCCESS payments can be refunded.");
        }
        return new Payment(id, user, merchant, wallet, amount, type, "REFUNDED", createdAt, version);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public User getUser() { return user; }
    public Merchant getMerchant() { return merchant; }
    public Wallet getWallet() { return wallet; }
    public BigDecimal getAmount() { return amount; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
}
