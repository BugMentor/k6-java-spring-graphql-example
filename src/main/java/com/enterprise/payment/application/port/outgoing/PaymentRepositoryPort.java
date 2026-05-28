package com.enterprise.payment.application.port.outgoing;

import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.PaymentSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {
    Payment save(Payment payment);
    
    void saveAll(List<Payment> payments); // For Batch Processing
    
    Payment update(Payment payment); // For Refund/Status updates with version check
    
    Optional<Payment> findById(UUID id);
    
    List<Payment> findByCustomerIdAndStatus(String customerId, String status, int limit);
    
    PaymentSummary getSummaryReport(Instant startDate, Instant endDate);

    // Dynamic Search for DB Planner Stress
    List<Payment> search(BigDecimal minAmount, BigDecimal maxAmount, String currency, String status, int page, int size);
}
