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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class L1_TestListUserPaymentsUseCase {

    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;

    private ListUserPaymentsUseCase listUserPaymentsUseCase;

    private User testUser;
    private Merchant testMerchant;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        listUserPaymentsUseCase = new ListUserPaymentsUseCase(paymentRepositoryPort);

        testUser = new User(UUID.randomUUID(), "list@test.com", "List User", "ACTIVE");
        testMerchant = new Merchant(UUID.randomUUID(), "List Merchant", "LIST-001");
        testWallet = new Wallet(UUID.randomUUID(), testUser, new BigDecimal("1000.00"), "USD");
    }

    @Test
    @DisplayName("Should return list of payments for user")
    void listPayments() {
        String userId = "USER-123";
        String status = "SUCCESS";
        int limit = 10;

        Payment payment = new Payment(UUID.randomUUID(), testUser, testMerchant, testWallet, new BigDecimal("10"), "CREDIT", status, Instant.now(), 0L);

        when(paymentRepositoryPort.findByCustomerIdAndStatus(userId, status, limit))
            .thenReturn(List.of(payment));

        List<Payment> result = listUserPaymentsUseCase.execute(userId, status, limit);

        assertEquals(1, result.size());
    }
}