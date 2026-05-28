package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.PaymentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class L1_TestGetPaymentSummaryUseCase {

    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;

    private GetPaymentSummaryUseCase getPaymentSummaryUseCase;

    @BeforeEach
    void setUp() {
        getPaymentSummaryUseCase = new GetPaymentSummaryUseCase(paymentRepositoryPort);
    }

    @Test
    @DisplayName("Should return summary report within date range")
    void getSummary() {
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        
        PaymentSummary summary = new PaymentSummary(Map.of("SUCCESS", new BigDecimal("1000")));
        
        when(paymentRepositoryPort.getSummaryReport(start, end)).thenReturn(summary);

        PaymentSummary result = getPaymentSummaryUseCase.execute(start, end);

        assertEquals(new BigDecimal("1000"), result.getTotalsByStatus().get("SUCCESS"));
    }
}
