package com.enterprise.payment.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.domain.model.Wallet;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("L2 - Wallet Repository Integration (PostgreSQL)")
class L2_TestWalletRepository extends AbstractPostgresDataJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private com.enterprise.payment.infrastructure.persistence.jpa.WalletJpaRepository walletRepository;

    @Autowired
    private com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository userRepository;

    private int userCounter = 0;
    
    private User createTestUser() {
        userCounter++;
        User user = new User(UUID.randomUUID(), "wallet" + userCounter + "@postgres.com", "Wallet User", "ACTIVE");
        return entityManager.persist(user);
    }

    @Test
    @DisplayName("Should save and retrieve wallet")
    void shouldSaveAndRetrieveWallet() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, new BigDecimal("500.00"), "USD");
        entityManager.persist(wallet);
        entityManager.flush();

        Optional<Wallet> found = walletRepository.findById(wallet.getId());
        
        assertTrue(found.isPresent());
        assertEquals(0, new BigDecimal("500.00").compareTo(found.get().getBalance()));
    }

    @Test
    @DisplayName("Should find wallet by user id")
    void shouldFindByUserId() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, new BigDecimal("100.00"), "EUR");
        entityManager.persist(wallet);
        entityManager.flush();

        var wallets = walletRepository.findAll();
        Optional<Wallet> found = wallets.stream()
            .filter(w -> w.getUser().getId().equals(user.getId()))
            .findFirst();
        
        assertTrue(found.isPresent());
        assertEquals("EUR", found.get().getCurrency());
    }

    @Test
    @DisplayName("Should return empty for non-existent id")
    void shouldReturnEmptyForNonExistent() {
        Optional<Wallet> found = walletRepository.findById(UUID.randomUUID());
        
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should delete wallet")
    void shouldDeleteWallet() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, new BigDecimal("50.00"), "USD");
        entityManager.persist(wallet);
        entityManager.flush();
        
        walletRepository.delete(wallet);
        entityManager.flush();

        Optional<Wallet> found = walletRepository.findById(wallet.getId());
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should handle zero balance wallet")
    void shouldHandleZeroBalance() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, BigDecimal.ZERO, "USD");
        entityManager.persist(wallet);
        entityManager.flush();

        Optional<Wallet> found = walletRepository.findById(wallet.getId());
        
        assertTrue(found.isPresent());
        assertEquals(0, BigDecimal.ZERO.compareTo(found.get().getBalance()));
    }

    @Test
    @DisplayName("Should handle large balance")
    void shouldHandleLargeBalance() {
        User user = createTestUser();
        Wallet wallet = new Wallet(UUID.randomUUID(), user, new BigDecimal("999999999.99"), "USD");
        entityManager.persist(wallet);
        entityManager.flush();

        Optional<Wallet> found = walletRepository.findById(wallet.getId());
        
        assertTrue(found.isPresent());
        assertEquals(0, new BigDecimal("999999999.99").compareTo(found.get().getBalance()));
    }

    @Test
    @DisplayName("Should persist wallet with different currencies")
    void shouldPersistMultipleCurrencies() {
        User user1 = createTestUser();
        User user2 = createTestUser();
        
        Wallet usdWallet = new Wallet(UUID.randomUUID(), user1, new BigDecimal("100"), "USD");
        Wallet eurWallet = new Wallet(UUID.randomUUID(), user2, new BigDecimal("200"), "EUR");
        
        entityManager.persist(usdWallet);
        entityManager.persist(eurWallet);
        entityManager.flush();

        var all = walletRepository.findAll();
        
        assertTrue(all.stream().anyMatch(w -> "USD".equals(w.getCurrency())));
        assertTrue(all.stream().anyMatch(w -> "EUR".equals(w.getCurrency())));
    }
}
