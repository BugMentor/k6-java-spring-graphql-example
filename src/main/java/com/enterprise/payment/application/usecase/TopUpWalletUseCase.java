package com.enterprise.payment.application.usecase;

import com.enterprise.payment.domain.model.Wallet;
import com.enterprise.payment.infrastructure.persistence.jpa.WalletJpaRepository;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TopUpWalletUseCase {

    private final WalletJpaRepository walletJpaRepository;

    public TopUpWalletUseCase(WalletJpaRepository walletJpaRepository) {
        this.walletJpaRepository = walletJpaRepository;
    }

    @Transactional
    @WithSpan("wallet-topup")
    public Wallet execute(@SpanAttribute("wallet.id") UUID walletId, 
                          @SpanAttribute("topup.amount") BigDecimal amount) {
        
        Wallet wallet = walletJpaRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        wallet.topUp(amount);

        return walletJpaRepository.save(wallet);
    }
}
