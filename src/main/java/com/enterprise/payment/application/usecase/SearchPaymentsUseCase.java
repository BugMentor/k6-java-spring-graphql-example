package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Payment;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SearchPaymentsUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public SearchPaymentsUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    @WithSpan("search-payments")
    public List<Payment> execute(BigDecimal minAmount, BigDecimal maxAmount, String currency, String status, int page, int size) {
        return paymentRepositoryPort.search(minAmount, maxAmount, currency, status, page, size);
    }
}
