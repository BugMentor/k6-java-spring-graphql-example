package com.enterprise.payment.infrastructure.persistence.jpa;

import com.enterprise.payment.domain.model.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MerchantJpaRepository extends JpaRepository<Merchant, UUID> {
    
    @Query("SELECT p.status, COUNT(p), SUM(p.amount) " +
           "FROM Payment p " +
           "WHERE p.merchant.id = :merchantId " +
           "AND p.createdAt BETWEEN :start AND :end " +
           "GROUP BY p.status")
    List<Object[]> getReconciliationReport(@Param("merchantId") UUID merchantId, 
                                           @Param("start") Instant start, 
                                           @Param("end") Instant end);
}
