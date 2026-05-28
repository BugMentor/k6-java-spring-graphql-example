package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Payment;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListUserPaymentsUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public ListUserPaymentsUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    @WithSpan("list-user-payments")
    public List<Payment> execute(
            @SpanAttribute("user.id") String userId,
            @SpanAttribute("payment.status") String status,
            @SpanAttribute("query.limit") int limit) {
        return paymentRepositoryPort.findByCustomerIdAndStatus(userId, status, limit);
    }
}
