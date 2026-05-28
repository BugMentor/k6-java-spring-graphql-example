package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Payment;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class GetPaymentUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public GetPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    @WithSpan("get-payment-by-id")
    public Optional<Payment> execute(@SpanAttribute("payment.id") UUID id) {
        return paymentRepositoryPort.findById(id);
    }
}
