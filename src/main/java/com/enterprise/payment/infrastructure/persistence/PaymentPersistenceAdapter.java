package com.enterprise.payment.infrastructure.persistence;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.PaymentSummary;
import com.enterprise.payment.infrastructure.persistence.jpa.PaymentJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository repository;

    public PaymentPersistenceAdapter(PaymentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Payment save(Payment payment) {
        return repository.save(payment);
    }

    @Override
    public void saveAll(List<Payment> payments) {
        repository.saveAll(payments);
    }

    @Override
    public Payment update(Payment payment) {
        // JPA handles version check automatically during save if @Version is present
        return repository.save(payment);
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Payment> findByCustomerIdAndStatus(String customerId, String status, int limit) {
        // Simple implementation using Specifications or derived queries
        return repository.findAll(Specification.where(hasCustomerId(customerId).and(hasStatus(status))), 
                PageRequest.of(0, limit)).getContent();
    }

    @Override
    public PaymentSummary getSummaryReport(Instant startDate, Instant endDate) {
        List<Object[]> results = repository.getSummaryReport(startDate, endDate);
        Map<String, BigDecimal> summaryMap = results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (BigDecimal) row[1]
                ));
        return new PaymentSummary(summaryMap);
    }

    @Override
    public List<Payment> search(BigDecimal minAmount, BigDecimal maxAmount, String type, String status, int page, int size) {
        Specification<Payment> spec = Specification.where(null);
        if (minAmount != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
        if (maxAmount != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
        if (type != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));

        return repository.findAll(spec, PageRequest.of(page, size)).getContent();
    }

    private Specification<Payment> hasCustomerId(String customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    private Specification<Payment> hasStatus(String status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
