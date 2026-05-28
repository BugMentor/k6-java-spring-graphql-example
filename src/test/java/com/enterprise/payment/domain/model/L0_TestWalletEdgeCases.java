package com.enterprise.payment.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class L0_TestWalletEdgeCases {

    private User createTestUser() {
        return new User(UUID.randomUUID(), "test@test.com", "Test User", "ACTIVE");
    }

    @Test
    @DisplayName("Wallet with zero initial balance")
    void zeroInitialBalance() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, BigDecimal.ZERO, "USD");
        
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()));
    }

    @Test
    @DisplayName("Wallet with maximum balance")
    void maximumBalance() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, new BigDecimal("999999999.99"), "USD");
        
        assertEquals(0, new BigDecimal("999999999.99").compareTo(wallet.getBalance()));
    }

    @Test
    @DisplayName("Multiple top-ups in sequence")
    void multipleTopUps() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, new BigDecimal("100.00"), "USD");
        
        wallet.topUp(new BigDecimal("50.00"));
        wallet.topUp(new BigDecimal("25.00"));
        wallet.topUp(new BigDecimal("25.00"));
        
        assertEquals(0, new BigDecimal("200.00").compareTo(wallet.getBalance()));
    }

    @Test
    @DisplayName("Multiple debits in sequence")
    void multipleDebits() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, new BigDecimal("200.00"), "USD");
        
        wallet.debit(new BigDecimal("50.00"));
        wallet.debit(new BigDecimal("75.00"));
        
        assertEquals(0, new BigDecimal("75.00").compareTo(wallet.getBalance()));
    }

    @Test
    @DisplayName("Debit exactly remaining balance")
    void debitExactBalance() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, new BigDecimal("100.00"), "USD");
        
        wallet.debit(new BigDecimal("100.00"));
        
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()));
    }

    @Test
    @DisplayName("Top-up with decimal precision")
    void topUpDecimalPrecision() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, BigDecimal.ZERO, "USD");
        
        wallet.topUp(new BigDecimal("0.01"));
        wallet.topUp(new BigDecimal("0.02"));
        wallet.topUp(new BigDecimal("0.03"));
        
        assertEquals(0, new BigDecimal("0.06").compareTo(wallet.getBalance()));
    }

    @Test
    @DisplayName("Wallet currency is stored correctly")
    void walletCurrency() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, BigDecimal.ZERO, "EUR");
        
        assertEquals("EUR", wallet.getCurrency());
    }

    @Test
    @DisplayName("Version is null for new unsaved entity")
    void versionIsNullForNewEntity() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, new BigDecimal("100.00"), "USD");
        
        assertNull(wallet.getVersion());
    }

    @Test
    @DisplayName("Currency can be null")
    void nullCurrencyIsAllowed() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, BigDecimal.ZERO, null);
        
        assertNull(wallet.getCurrency());
    }
}