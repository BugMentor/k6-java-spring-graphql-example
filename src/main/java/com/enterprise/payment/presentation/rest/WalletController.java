package com.enterprise.payment.presentation.rest;

import com.enterprise.payment.domain.model.Wallet;
import com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.WalletJpaRepository;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/v1/wallets")
public class WalletController {

    private final WalletJpaRepository walletJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public WalletController(WalletJpaRepository walletJpaRepository, UserJpaRepository userJpaRepository) {
        this.walletJpaRepository = walletJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @PostMapping
    @WithSpan("create-wallet")
    public ResponseEntity<Wallet> createWallet(
            @SpanAttribute("wallet.userId") @RequestBody CreateWalletRequest request) {
        var userOpt = userJpaRepository.findById(request.userId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Wallet wallet = new Wallet(
                UUID.randomUUID(),
                userOpt.get(),
                request.balance() != null ? request.balance() : BigDecimal.ZERO,
                request.currency() != null ? request.currency() : "USD"
        );
        Wallet saved = walletJpaRepository.save(wallet);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    @WithSpan("get-wallet")
    public ResponseEntity<Wallet> getWallet(@PathVariable UUID id) {
        return walletJpaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @WithSpan("list-wallets")
    public ResponseEntity<java.util.List<Wallet>> listWallets() {
        return ResponseEntity.ok(walletJpaRepository.findAll());
    }

    @GetMapping("/user/{userId}")
    @WithSpan("get-wallet-by-user")
    public ResponseEntity<Wallet> getWalletByUser(@PathVariable UUID userId) {
        return walletJpaRepository.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record CreateWalletRequest(UUID userId, BigDecimal balance, String currency) {}
}