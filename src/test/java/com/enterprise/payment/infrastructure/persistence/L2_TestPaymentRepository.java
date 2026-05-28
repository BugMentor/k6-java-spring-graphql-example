package com.enterprise.payment.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.domain.model.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("L2 - Payment Repository Integration (PostgreSQL)")
class L2_TestPaymentRepository extends AbstractPostgresDataJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private com.enterprise.payment.infrastructure.persistence.jpa.PaymentJpaRepository paymentRepository;

    private User createTestUser() {
        User user = new User(UUID.randomUUID(), "payment@postgres.com", "Payment User", "ACTIVE");
        return entityManager.persist(user);
    }

    private Merchant createTestMerchant() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Pay Merchant", "PAY-KEY");
        return entityManager.persist(merchant);
    }

    @Test
    @DisplayName("Should save and retrieve payment")
    void shouldSaveAndRetrievePayment() {
        User user = createTestUser();
        Merchant merchant = createTestMerchant();
        
        Payment payment = new Payment(
            UUID.randomUUID(), user, merchant, null,
            new BigDecimal("150.00"), "DEBIT", "PENDING",
            Instant.now(), null
        );
        entityManager.persist(payment);
        entityManager.flush();

        Optional<Payment> found = paymentRepository.findById(payment.getId());
        
        assertTrue(found.isPresent());
        assertEquals("PENDING", found.get().getStatus());
    }

    @Test
    @DisplayName("Should find payments by user id")
    void shouldFindByUserId() {
        User user = createTestUser();
        Merchant merchant = createTestMerchant();
        
        Payment p1 = new Payment(UUID.randomUUID(), user, merchant, null, new BigDecimal("50"), "DEBIT", "PENDING", Instant.now(), null);
        Payment p2 = new Payment(UUID.randomUUID(), user, merchant, null, new BigDecimal("75"), "DEBIT", "SUCCESS", Instant.now(), null);
        
        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.flush();

        List<Payment> userPayments = paymentRepository.findAll().stream()
            .filter(p -> p.getUser().getId().equals(user.getId()))
            .toList();
        
        assertEquals(2, userPayments.size());
    }

    @Test
    @DisplayName("Should find payments by status")
    void shouldFindByStatus() {
        User user = createTestUser();
        Merchant merchant = createTestMerchant();
        
        Payment p1 = new Payment(UUID.randomUUID(), user, merchant, null, new BigDecimal("100"), "DEBIT", "SUCCESS", Instant.now(), null);
        
        entityManager.persist(p1);
        entityManager.flush();

        List<Payment> successPayments = paymentRepository.findAll().stream()
            .filter(p -> "SUCCESS".equals(p.getStatus()))
            .toList();
        
        assertTrue(successPayments.size() >= 1);
    }

    @Test
    @DisplayName("Should return empty for non-existent id")
    void shouldReturnEmptyForNonExistent() {
        Optional<Payment> found = paymentRepository.findById(UUID.randomUUID());
        
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should delete payment")
    void shouldDeletePayment() {
        User user = createTestUser();
        Merchant merchant = createTestMerchant();
        
        Payment payment = new Payment(UUID.randomUUID(), user, merchant, null, new BigDecimal("50"), "DEBIT", "PENDING", Instant.now(), null);
        entityManager.persist(payment);
        entityManager.flush();
        
        paymentRepository.delete(payment);
        entityManager.flush();

        Optional<Payment> found = paymentRepository.findById(payment.getId());
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should handle credit payment type")
    void shouldHandleCreditPayment() {
        User user = createTestUser();
        Merchant merchant = createTestMerchant();
        
        Payment creditPayment = new Payment(
            UUID.randomUUID(), user, merchant, null,
            new BigDecimal("200.00"), "CREDIT", "PENDING",
            Instant.now(), null
        );
        entityManager.persist(creditPayment);
        entityManager.flush();

        Optional<Payment> found = paymentRepository.findById(creditPayment.getId());
        
        assertTrue(found.isPresent());
        assertEquals("CREDIT", found.get().getType());
    }

    @Test
    @DisplayName("Should handle refunded status")
    void shouldHandleRefundedStatus() {
        User user = createTestUser();
        Merchant merchant = createTestMerchant();
        
        Payment payment = new Payment(
            UUID.randomUUID(), user, merchant, null,
            new BigDecimal("100.00"), "DEBIT", "REFUNDED",
            Instant.now(), 1L
        );
        entityManager.persist(payment);
        entityManager.flush();

        Optional<Payment> found = paymentRepository.findById(payment.getId());
        
        assertTrue(found.isPresent());
        assertEquals("REFUNDED", found.get().getStatus());
    }

    @Test
    @DisplayName("Should persist multiple payments with different amounts")
    void shouldPersistMultiplePayments() {
        User user = createTestUser();
        Merchant merchant = createTestMerchant();
        
        entityManager.persist(new Payment(UUID.randomUUID(), user, merchant, null, new BigDecimal("10.00"), "DEBIT", "PENDING", Instant.now(), null));
        entityManager.persist(new Payment(UUID.randomUUID(), user, merchant, null, new BigDecimal("20.50"), "DEBIT", "PENDING", Instant.now(), null));
        entityManager.persist(new Payment(UUID.randomUUID(), user, merchant, null, new BigDecimal("0.01"), "DEBIT", "PENDING", Instant.now(), null));
        entityManager.flush();

        List<Payment> all = paymentRepository.findAll();
        assertTrue(all.size() >= 3);
    }
}
