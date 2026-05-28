package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Payment;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RefundPaymentUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public RefundPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    @WithSpan("refund-payment")
    public Payment execute(@SpanAttribute("payment.id") UUID id) {
        Payment existing = paymentRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        
        Payment refunded = existing.refund();
        return paymentRepositoryPort.update(refunded);
    }
}
