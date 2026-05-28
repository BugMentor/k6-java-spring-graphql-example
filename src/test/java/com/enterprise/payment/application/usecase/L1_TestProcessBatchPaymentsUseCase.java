package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.domain.model.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class L1_TestProcessBatchPaymentsUseCase {

    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;

    private ProcessBatchPaymentsUseCase processBatchPaymentsUseCase;

    private User testUser;
    private Merchant testMerchant;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        processBatchPaymentsUseCase = new ProcessBatchPaymentsUseCase(paymentRepositoryPort);

        testUser = new User(UUID.randomUUID(), "batch@test.com", "Batch User", "ACTIVE");
        testMerchant = new Merchant(UUID.randomUUID(), "Batch Merchant", "BATCH-001");
        testWallet = new Wallet(UUID.randomUUID(), testUser, new BigDecimal("5000.00"), "USD");
    }

    @Test
    @DisplayName("Should successfully process a batch of valid payments")
    void processBatchSuccess() {
        List<Payment> batch = List.of(
            new Payment(UUID.randomUUID(), testUser, testMerchant, testWallet, new BigDecimal("10"), "CREDIT", "SUCCESS", Instant.now(), 0L),
            new Payment(UUID.randomUUID(), testUser, testMerchant, testWallet, new BigDecimal("20"), "DEBIT", "SUCCESS", Instant.now(), 0L)
        );

        processBatchPaymentsUseCase.execute(batch);

        verify(paymentRepositoryPort, times(1)).saveAll(batch);
    }

    @Test
    @DisplayName("Should throw exception if batch exceeds 500 payments")
    void batchTooLarge() {
        List<Payment> batch = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            batch.add(new Payment(UUID.randomUUID(), testUser, testMerchant, testWallet, BigDecimal.TEN, "CREDIT", "SUCCESS", Instant.now(), 0L));
        }

        assertThrows(IllegalArgumentException.class, () -> processBatchPaymentsUseCase.execute(batch));
        verify(paymentRepositoryPort, never()).saveAll(anyList());
    }
}