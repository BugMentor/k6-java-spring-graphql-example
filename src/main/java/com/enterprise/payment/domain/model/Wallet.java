package com.enterprise.payment.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private String currency;

    @Version
    private Long version;

    protected Wallet() {}

    public Wallet(UUID id, User user, BigDecimal balance, String currency) {
        this.id = id != null ? id : UUID.randomUUID();
        this.user = user;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.currency = currency;
    }

    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in wallet: " + id);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void topUp(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    public UUID getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public User getUser() { return user; }
    public String getCurrency() { return currency; }
    public Long getVersion() { return version; }
}
