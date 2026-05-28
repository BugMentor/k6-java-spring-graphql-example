package com.enterprise.payment.presentation.graphql;

import com.enterprise.payment.domain.model.Wallet;
import com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.WalletJpaRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
public class WalletGraphQLController {

    private final WalletJpaRepository walletJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public WalletGraphQLController(WalletJpaRepository walletJpaRepository, UserJpaRepository userJpaRepository) {
        this.walletJpaRepository = walletJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @QueryMapping
    public Wallet wallet(@Argument String id) {
        return walletJpaRepository.findById(UUID.fromString(id)).orElse(null);
    }

    @QueryMapping
    public List<Wallet> wallets() {
        return walletJpaRepository.findAll();
    }

    @QueryMapping
    public Wallet walletByUserId(@Argument String userId) {
        return walletJpaRepository.findByUserId(UUID.fromString(userId)).orElse(null);
    }

    @MutationMapping
    public Wallet createWallet(@Argument String userId, @Argument Float balance, @Argument String currency) {
        var user = userJpaRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Wallet wallet = new Wallet(UUID.randomUUID(), user,
                balance != null ? BigDecimal.valueOf(balance) : BigDecimal.ZERO,
                currency != null ? currency : "USD");
        return walletJpaRepository.save(wallet);
    }

    @SchemaMapping(typeName = "Wallet", field = "userId")
    public String walletUserId(Wallet wallet) {
        return wallet.getUser() != null ? wallet.getUser().getId().toString() : null;
    }
}
