package com.enterprise.payment.infrastructure.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class L1_TestOtelConfig {

    @Test
    @DisplayName("Should be able to get a tracer from GlobalOpenTelemetry")
    void getTracer() {
        Tracer tracer = GlobalOpenTelemetry.getTracer("payment-service-test");
        Span span = tracer.spanBuilder("test-span").startSpan();
        try {
            assertNotNull(span);
        } finally {
            span.end();
        }
    }
}
