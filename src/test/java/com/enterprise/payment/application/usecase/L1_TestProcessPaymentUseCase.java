package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.domain.model.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessPaymentUseCase Tests")
class L1_TestProcessPaymentUseCase {

    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;

    private ProcessPaymentUseCase processPaymentUseCase;

    private User testUser;
    private Merchant testMerchant;
    private Wallet testWallet;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        processPaymentUseCase = new ProcessPaymentUseCase(paymentRepositoryPort);

        testUser = new User(UUID.randomUUID(), "user@test.com", "Test User", "ACTIVE");
        testMerchant = new Merchant(UUID.randomUUID(), "Test Merchant", "MERCH-001");
        testWallet = new Wallet(UUID.randomUUID(), testUser, new BigDecimal("1000.00"), "USD");

        testPayment = new Payment(
            UUID.randomUUID(),
            testUser,
            testMerchant,
            testWallet,
            new BigDecimal("100.00"),
            "DEBIT",
            "PENDING",
            Instant.now(),
            null
        );
    }

    @Nested
    @DisplayName("Execute Method Tests")
    class ExecuteTests {

        @Test
        @DisplayName("Should save payment successfully")
        void shouldSavePaymentSuccessfully() {
            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(testPayment);

            Payment result = processPaymentUseCase.execute(testPayment);

            assertNotNull(result);
            assertEquals(testPayment.getId(), result.getId());
            verify(paymentRepositoryPort).save(testPayment);
        }

        @Test
        @DisplayName("Should return saved payment with generated ID")
        void shouldReturnSavedPaymentWithGeneratedId() {
            UUID generatedId = UUID.randomUUID();
            Payment paymentWithGeneratedId = new Payment(
                generatedId,
                testUser,
                testMerchant,
                testWallet,
                new BigDecimal("50.00"),
                "CREDIT",
                "SUCCESS",
                Instant.now(),
                1L
            );

            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(paymentWithGeneratedId);

            Payment result = processPaymentUseCase.execute(testPayment);

            assertEquals(generatedId, result.getId());
        }

        @Test
        @DisplayName("Should delegate to repository save method")
        void shouldDelegateToRepository() {
            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(testPayment);

            processPaymentUseCase.execute(testPayment);

            verify(paymentRepositoryPort).save(any(Payment.class));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle large payment amount")
        void shouldHandleLargeAmount() {
            Payment largePayment = new Payment(
                UUID.randomUUID(),
                testUser,
                testMerchant,
                testWallet,
                new BigDecimal("999999.99"),
                "DEBIT",
                "PENDING",
                Instant.now(),
                null
            );

            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(largePayment);

            Payment result = processPaymentUseCase.execute(largePayment);

            assertEquals(new BigDecimal("999999.99"), result.getAmount());
        }

        @Test
        @DisplayName("Should handle zero amount payment")
        void shouldHandleZeroAmount() {
            Payment zeroPayment = new Payment(
                UUID.randomUUID(),
                testUser,
                testMerchant,
                testWallet,
                BigDecimal.ZERO,
                "DEBIT",
                "PENDING",
                Instant.now(),
                null
            );

            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(zeroPayment);

            Payment result = processPaymentUseCase.execute(zeroPayment);

            assertEquals(BigDecimal.ZERO, result.getAmount());
        }
    }
}