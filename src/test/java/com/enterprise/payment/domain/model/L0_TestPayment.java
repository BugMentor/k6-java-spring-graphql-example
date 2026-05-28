package com.enterprise.payment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Payment Domain Entity Tests")
class L0_TestPayment {

    private User createTestUser() {
        return new User(UUID.randomUUID(), "user@test.com", "Test User", "ACTIVE");
    }

    private Merchant createTestMerchant() {
        return new Merchant(UUID.randomUUID(), "Test Merchant", "MERCH-001");
    }

    private Wallet createTestWallet() {
        return new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("1000.00"), "USD");
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create Payment with all valid parameters")
        void shouldCreatePaymentWithValidParameters() {
            UUID id = UUID.randomUUID();
            User user = createTestUser();
            Merchant merchant = createTestMerchant();
            Wallet wallet = createTestWallet();
            BigDecimal amount = new BigDecimal("100.00");
            String type = "DEBIT";
            String status = "PENDING";
            Instant createdAt = Instant.now();

            Payment payment = new Payment(id, user, merchant, wallet, amount, type, status, createdAt, null);

            assertEquals(id, payment.getId());
            assertEquals(user, payment.getUser());
            assertEquals(merchant, payment.getMerchant());
            assertEquals(wallet, payment.getWallet());
            assertEquals(amount, payment.getAmount());
            assertEquals(type, payment.getType());
            assertEquals(status, payment.getStatus());
        }

        @Test
        @DisplayName("Should generate UUID when null is provided")
        void shouldGenerateUuidWhenNull() {
            Payment payment = new Payment(
                null,
                createTestUser(),
                createTestMerchant(),
                createTestWallet(),
                new BigDecimal("100"),
                "DEBIT",
                "PENDING",
                Instant.now(),
                null
            );

            assertNotNull(payment.getId());
        }

        @Test
        @DisplayName("Should default status to PENDING when null is provided")
        void shouldDefaultStatusToPending() {
            Payment payment = new Payment(
                UUID.randomUUID(),
                createTestUser(),
                createTestMerchant(),
                createTestWallet(),
                new BigDecimal("100"),
                "DEBIT",
                null,
                Instant.now(),
                null
            );

            assertEquals("PENDING", payment.getStatus());
        }

        @Test
        @DisplayName("Should allow null wallet")
        void shouldAllowNullWallet() {
            Payment payment = new Payment(
                UUID.randomUUID(),
                createTestUser(),
                createTestMerchant(),
                null,
                new BigDecimal("100"),
                "DEBIT",
                "PENDING",
                Instant.now(),
                null
            );

            assertNull(payment.getWallet());
        }
    }

    @Nested
    @DisplayName("Refund Operation")
    class RefundTests {

        @Test
        @DisplayName("Should successfully refund SUCCESS payment")
        void shouldRefundSuccessPayment() {
            Payment payment = new Payment(
                UUID.randomUUID(),
                createTestUser(),
                createTestMerchant(),
                createTestWallet(),
                new BigDecimal("100.00"),
                "DEBIT",
                "SUCCESS",
                Instant.now(),
                1L
            );

            Payment refundedPayment = payment.refund();

            assertEquals("REFUNDED", refundedPayment.getStatus());
            assertEquals(payment.getId(), refundedPayment.getId());
            assertEquals(payment.getAmount(), refundedPayment.getAmount());
        }

        @Test
        @DisplayName("Should preserve original payment ID in refund")
        void shouldPreserveId() {
            UUID originalId = UUID.randomUUID();
            Payment payment = new Payment(
                originalId,
                createTestUser(),
                createTestMerchant(),
                createTestWallet(),
                new BigDecimal("100.00"),
                "DEBIT",
                "SUCCESS",
                Instant.now(),
                1L
            );

            Payment refundedPayment = payment.refund();

            assertEquals(originalId, refundedPayment.getId());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when refunding PENDING payment")
        void shouldThrowWhenRefundingPendingPayment() {
            Payment payment = new Payment(
                UUID.randomUUID(),
                createTestUser(),
                createTestMerchant(),
                createTestWallet(),
                new BigDecimal("100.00"),
                "DEBIT",
                "PENDING",
                Instant.now(),
                1L
            );

            assertThrows(IllegalStateException.class, payment::refund);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when refunding FAILED payment")
        void shouldThrowWhenRefundingFailedPayment() {
            Payment payment = new Payment(
                UUID.randomUUID(),
                createTestUser(),
                createTestMerchant(),
                createTestWallet(),
                new BigDecimal("100.00"),
                "DEBIT",
                "FAILED",
                Instant.now(),
                1L
            );

            assertThrows(IllegalStateException.class, payment::refund);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when refunding REFUNDED payment")
        void shouldThrowWhenRefundingRefundedPayment() {
            Payment payment = new Payment(
                UUID.randomUUID(),
                createTestUser(),
                createTestMerchant(),
                createTestWallet(),
                new BigDecimal("100.00"),
                "DEBIT",
                "REFUNDED",
                Instant.now(),
                1L
            );

            assertThrows(IllegalStateException.class, payment::refund);
        }

        @Test
        @DisplayName("Should copy all fields to refunded payment except status")
        void shouldCopyAllFields() {
            User user = createTestUser();
            Merchant merchant = createTestMerchant();
            Wallet wallet = createTestWallet();
            BigDecimal amount = new BigDecimal("250.00");
            Instant createdAt = Instant.parse("2024-01-15T10:00:00Z");

            Payment payment = new Payment(
                UUID.randomUUID(),
                user,
                merchant,
                wallet,
                amount,
                "CREDIT",
                "SUCCESS",
                createdAt,
                5L
            );

            Payment refunded = payment.refund();

            assertEquals(user, refunded.getUser());
            assertEquals(merchant, refunded.getMerchant());
            assertEquals(wallet, refunded.getWallet());
            assertEquals(amount, refunded.getAmount());
            assertEquals("CREDIT", refunded.getType());
            assertEquals(createdAt, refunded.getCreatedAt());
            assertEquals(5L, refunded.getVersion());
        }
    }

    @Nested
    @DisplayName("Version Field (Optimistic Locking)")
    class VersionTests {

        @Test
        @DisplayName("Should store version")
        void shouldStoreVersion() {
            Payment payment = new Payment(
                UUID.randomUUID(),
                createTestUser(),
                createTestMerchant(),
                createTestWallet(),
                new BigDecimal("100"),
                "DEBIT",
                "PENDING",
                Instant.now(),
                42L
            );

            assertEquals(42L, payment.getVersion());
        }

        @Test
        @DisplayName("Should allow null version")
        void shouldAllowNullVersion() {
            Payment payment = new Payment(
                UUID.randomUUID(),
                createTestUser(),
                createTestMerchant(),
                createTestWallet(),
                new BigDecimal("100"),
                "DEBIT",
                "PENDING",
                Instant.now(),
                null
            );

            assertNull(payment.getVersion());
        }
    }
}