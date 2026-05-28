package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Payment;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProcessBatchPaymentsUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public ProcessBatchPaymentsUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    @WithSpan("process-batch-payments")
    public void execute(@SpanAttribute("batch.size") List<Payment> payments) {
        if (payments.size() > 500) {
            throw new IllegalArgumentException("Batch size cannot exceed 500 payments");
        }
        paymentRepositoryPort.saveAll(payments);
    }
}
