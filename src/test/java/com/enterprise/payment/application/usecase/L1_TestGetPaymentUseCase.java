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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class L1_TestGetPaymentUseCase {

    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;

    private GetPaymentUseCase getPaymentUseCase;

    private User testUser;
    private Merchant testMerchant;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        getPaymentUseCase = new GetPaymentUseCase(paymentRepositoryPort);

        testUser = new User(UUID.randomUUID(), "user@test.com", "Test User", "ACTIVE");
        testMerchant = new Merchant(UUID.randomUUID(), "Test Merchant", "MERCH-001");
        testWallet = new Wallet(UUID.randomUUID(), testUser, new BigDecimal("1000.00"), "USD");
    }

    @Test
    @DisplayName("Should return payment when ID exists")
    void getPaymentSuccess() {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment(id, testUser, testMerchant, testWallet, new BigDecimal("100.00"), "CREDIT", "SUCCESS", Instant.now(), 0L);

        when(paymentRepositoryPort.findById(id)).thenReturn(Optional.of(payment));

        Optional<Payment> result = getPaymentUseCase.execute(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }
}
