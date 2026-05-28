package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Payment;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

@Service
public class ProcessPaymentUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public ProcessPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    @WithSpan("process-payment")
    public Payment execute(Payment payment) {
        return paymentRepositoryPort.save(payment);
    }
}
