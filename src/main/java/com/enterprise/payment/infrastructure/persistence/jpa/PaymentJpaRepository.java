package com.enterprise.payment.infrastructure.persistence.jpa;

import com.enterprise.payment.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentJpaRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    
    @Query("SELECT p.status, SUM(p.amount) FROM Payment p WHERE p.createdAt BETWEEN :start AND :end GROUP BY p.status")
    List<Object[]> getSummaryReport(@Param("start") Instant start, @Param("end") Instant end);
}
