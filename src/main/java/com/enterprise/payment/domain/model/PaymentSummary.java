package com.enterprise.payment.domain.model;

import java.math.BigDecimal;
import java.util.Map;

public class PaymentSummary {
    private final Map<String, BigDecimal> totalsByStatus;

    public PaymentSummary(Map<String, BigDecimal> totalsByStatus) {
        this.totalsByStatus = totalsByStatus;
    }

    public Map<String, BigDecimal> getTotalsByStatus() {
        return totalsByStatus;
    }
}
