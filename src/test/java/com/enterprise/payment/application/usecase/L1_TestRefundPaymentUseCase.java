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
class L1_TestRefundPaymentUseCase {

    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;

    private RefundPaymentUseCase refundPaymentUseCase;

    private User testUser;
    private Merchant testMerchant;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        refundPaymentUseCase = new RefundPaymentUseCase(paymentRepositoryPort);

        testUser = new User(UUID.randomUUID(), "refund@test.com", "Refund User", "ACTIVE");
        testMerchant = new Merchant(UUID.randomUUID(), "Refund Merchant", "REFUND-001");
        testWallet = new Wallet(UUID.randomUUID(), testUser, new BigDecimal("1000.00"), "USD");
    }

    @Test
    @DisplayName("Should successfully refund a SUCCESS payment")
    void refundSuccess() {
        UUID id = UUID.randomUUID();
        Payment existing = new Payment(id, testUser, testMerchant, testWallet, new BigDecimal("100"), "CREDIT", "SUCCESS", Instant.now(), 1L);

        when(paymentRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(paymentRepositoryPort.update(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = refundPaymentUseCase.execute(id);

        assertEquals("REFUNDED", result.getStatus());
        verify(paymentRepositoryPort, times(1)).update(any());
    }

    @Test
    @DisplayName("Should throw exception when refunding a PENDING payment")
    void refundInvalidState() {
        UUID id = UUID.randomUUID();
        Payment existing = new Payment(id, testUser, testMerchant, testWallet, new BigDecimal("100"), "CREDIT", "PENDING", Instant.now(), 1L);

        when(paymentRepositoryPort.findById(id)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> refundPaymentUseCase.execute(id));
        verify(paymentRepositoryPort, never()).update(any());
    }
}