package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.Wallet;
import com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.WalletJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.MerchantJpaRepository;
import com.enterprise.payment.domain.model.Merchant;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class WalletTransferUseCase {

    private final WalletJpaRepository walletJpaRepository;
    private final MerchantJpaRepository merchantJpaRepository;
    private final PaymentRepositoryPort paymentRepositoryPort;

    public WalletTransferUseCase(WalletJpaRepository walletJpaRepository, 
                                 MerchantJpaRepository merchantJpaRepository,
                                 PaymentRepositoryPort paymentRepositoryPort) {
        this.walletJpaRepository = walletJpaRepository;
        this.merchantJpaRepository = merchantJpaRepository;
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    @Transactional
    @WithSpan("wallet-transfer")
    public Payment execute(@SpanAttribute("wallet.id") UUID walletId, 
                           @SpanAttribute("merchant.id") UUID merchantId, 
                           @SpanAttribute("payment.amount") BigDecimal amount) {
        
        Wallet wallet = walletJpaRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        Merchant merchant = merchantJpaRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));

        try {
            wallet.debit(amount);
        } catch (IllegalArgumentException e) {
            Span.current().setAttribute("payment.rejection_reason", "insufficient_funds");
            throw e;
        }

        walletJpaRepository.save(wallet);

        Payment payment = new Payment(
                null, 
                wallet.getUser(), 
                merchant, 
                wallet, 
                amount, 
                "WALLET_TRANSFER", 
                "SUCCESS", 
                Instant.now(), 
                0L
        );
        
        return paymentRepositoryPort.save(payment);
    }
}
