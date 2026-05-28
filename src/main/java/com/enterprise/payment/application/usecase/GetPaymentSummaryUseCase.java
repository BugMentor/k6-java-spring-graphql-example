package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.PaymentSummary;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class GetPaymentSummaryUseCase {
    private final PaymentRepositoryPort paymentRepositoryPort;

    public GetPaymentSummaryUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    @WithSpan("get-payment-summary-report")
    public PaymentSummary execute(
            @SpanAttribute("report.start_date") Instant startDate,
            @SpanAttribute("report.end_date") Instant endDate) {
        return paymentRepositoryPort.getSummaryReport(startDate, endDate);
    }
}
