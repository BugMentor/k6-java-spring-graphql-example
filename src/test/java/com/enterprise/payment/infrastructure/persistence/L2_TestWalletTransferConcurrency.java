package com.enterprise.payment.infrastructure.persistence;

import com.enterprise.payment.application.usecase.WalletTransferUseCase;
import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.domain.model.Wallet;
import com.enterprise.payment.infrastructure.persistence.jpa.MerchantJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.WalletJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("L2 - Wallet Transfer Concurrency (PostgreSQL)")
class L2_TestWalletTransferConcurrency extends AbstractPostgresIntegrationTest {

    @Autowired
    private WalletTransferUseCase walletTransferUseCase;

    @Autowired
    private WalletJpaRepository walletJpaRepository;

    @Autowired
    private MerchantJpaRepository merchantJpaRepository;

    @Test
    @DisplayName("Should handle 10 concurrent transfers without race conditions")
    void testConcurrentTransfers() throws InterruptedException {
        User concurrencyUser = new User(null, "concurrency@postgres.com", "Concurrency User", "ACTIVE");
        userJpaRepository.save(concurrencyUser);

        Wallet wallet = new Wallet(null, concurrencyUser, new BigDecimal("100.00"), "USD");
        walletJpaRepository.save(wallet);

        Merchant merchant = new Merchant(null, "Merchant-1", "key-1");
        merchantJpaRepository.save(merchant);

        int threads = 10;
        BigDecimal transferAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                try {
                    walletTransferUseCase.execute(wallet.getId(), merchant.getId(), transferAmount);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Transfer failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Wallet updatedWallet = walletJpaRepository.findById(wallet.getId()).get();
        assertEquals(0, updatedWallet.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals(threads, successCount.get());
    }
}
