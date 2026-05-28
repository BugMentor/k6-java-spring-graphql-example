package com.enterprise.payment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Wallet Domain Entity Tests")
class L0_TestWallet {

    private User createTestUser() {
        return new User(UUID.randomUUID(), "user@test.com", "Test User", "ACTIVE");
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create Wallet with all valid parameters")
        void shouldCreateWalletWithValidParameters() {
            UUID id = UUID.randomUUID();
            User user = createTestUser();
            BigDecimal balance = new BigDecimal("1000.00");
            String currency = "USD";

            Wallet wallet = new Wallet(id, user, balance, currency);

            assertEquals(id, wallet.getId());
            assertEquals(user, wallet.getUser());
            assertEquals(new BigDecimal("1000.00"), wallet.getBalance());
            assertEquals("USD", wallet.getCurrency());
        }

        @Test
        @DisplayName("Should generate UUID when null is provided")
        void shouldGenerateUuidWhenNull() {
            Wallet wallet = new Wallet(null, createTestUser(), new BigDecimal("100"), "USD");

            assertNotNull(wallet.getId());
        }

        @Test
        @DisplayName("Should default balance to ZERO when null is provided")
        void shouldDefaultBalanceToZero() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), null, "USD");

            assertEquals(BigDecimal.ZERO, wallet.getBalance());
        }
    }

    @Nested
    @DisplayName("Debit Operations")
    class DebitTests {

        @Test
        @DisplayName("Should successfully debit amount when sufficient balance")
        void shouldDebitSuccessfully() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("500.00"), "USD");

            wallet.debit(new BigDecimal("200.00"));

            assertEquals(new BigDecimal("300.00"), wallet.getBalance());
        }

        @Test
        @DisplayName("Should debit entire balance when amount equals balance")
        void shouldDebitEntireBalance() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100.00"), "USD");

            wallet.debit(new BigDecimal("100.00"));

            assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance().stripTrailingZeros()));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when insufficient funds")
        void shouldThrowWhenInsufficientFunds() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100.00"), "USD");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                wallet.debit(new BigDecimal("150.00"))
            );

            assertTrue(exception.getMessage().contains("Insufficient funds"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when debit amount is zero")
        void shouldThrowWhenDebitZero() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100.00"), "USD");

            assertThrows(IllegalArgumentException.class, () ->
                wallet.debit(BigDecimal.ZERO)
            );
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when debit amount is negative")
        void shouldThrowWhenDebitNegative() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100.00"), "USD");

            assertThrows(IllegalArgumentException.class, () ->
                wallet.debit(new BigDecimal("-50.00"))
            );
        }
    }

    @Nested
    @DisplayName("Top-up Operations")
    class TopUpTests {

        @Test
        @DisplayName("Should successfully top-up positive amount")
        void shouldTopUpSuccessfully() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100.00"), "USD");

            wallet.topUp(new BigDecimal("50.00"));

            assertEquals(new BigDecimal("150.00"), wallet.getBalance());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for zero top-up")
        void shouldThrowForZeroTopUp() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100.00"), "USD");

            assertThrows(IllegalArgumentException.class, () ->
                wallet.topUp(BigDecimal.ZERO)
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"-10", "-0.01", "-1000"})
        @DisplayName("Should throw IllegalArgumentException for negative top-up")
        void shouldThrowForNegativeTopUp(String negativeAmount) {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100.00"), "USD");

            assertThrows(IllegalArgumentException.class, () ->
                wallet.topUp(new BigDecimal(negativeAmount))
            );
        }

        @Test
        @DisplayName("Should handle very small top-up amount")
        void shouldHandleSmallTopUp() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100.00"), "USD");

            wallet.topUp(new BigDecimal("0.01"));

            assertEquals(new BigDecimal("100.01"), wallet.getBalance());
        }

        @Test
        @DisplayName("Should handle large top-up amount")
        void shouldHandleLargeTopUp() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100.00"), "USD");

            wallet.topUp(new BigDecimal("999999.99"));

            assertEquals(new BigDecimal("1000099.99"), wallet.getBalance());
        }
    }

    @Nested
    @DisplayName("Version Field (Optimistic Locking)")
    class VersionTests {

        @Test
        @DisplayName("Should have null version on new entity")
        void shouldHaveNullVersionOnNewEntity() {
            Wallet wallet = new Wallet(UUID.randomUUID(), createTestUser(), new BigDecimal("100"), "USD");

            assertNull(wallet.getVersion());
        }
    }
}