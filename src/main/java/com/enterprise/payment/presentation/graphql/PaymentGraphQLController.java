package com.enterprise.payment.presentation.graphql;

import com.enterprise.payment.application.usecase.GetPaymentSummaryUseCase;
import com.enterprise.payment.application.usecase.GetPaymentUseCase;
import com.enterprise.payment.application.usecase.ListUserPaymentsUseCase;
import com.enterprise.payment.application.usecase.ProcessBatchPaymentsUseCase;
import com.enterprise.payment.application.usecase.ProcessPaymentUseCase;
import com.enterprise.payment.application.usecase.RefundPaymentUseCase;
import com.enterprise.payment.application.usecase.SearchPaymentsUseCase;
import com.enterprise.payment.application.usecase.TopUpWalletUseCase;
import com.enterprise.payment.application.usecase.WalletTransferUseCase;
import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.PaymentSummary;
import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.infrastructure.persistence.jpa.MerchantJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class PaymentGraphQLController {

    private final GetPaymentUseCase getPaymentUseCase;
    private final ProcessPaymentUseCase processPaymentUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;
    private final ListUserPaymentsUseCase listUserPaymentsUseCase;
    private final SearchPaymentsUseCase searchPaymentsUseCase;
    private final GetPaymentSummaryUseCase getPaymentSummaryUseCase;
    private final ProcessBatchPaymentsUseCase processBatchPaymentsUseCase;
    private final WalletTransferUseCase walletTransferUseCase;
    private final TopUpWalletUseCase topUpWalletUseCase;
    private final UserJpaRepository userJpaRepository;
    private final MerchantJpaRepository merchantJpaRepository;

    public PaymentGraphQLController(GetPaymentUseCase getPaymentUseCase,
                                    ProcessPaymentUseCase processPaymentUseCase,
                                    RefundPaymentUseCase refundPaymentUseCase,
                                    ListUserPaymentsUseCase listUserPaymentsUseCase,
                                    SearchPaymentsUseCase searchPaymentsUseCase,
                                    GetPaymentSummaryUseCase getPaymentSummaryUseCase,
                                    ProcessBatchPaymentsUseCase processBatchPaymentsUseCase,
                                    WalletTransferUseCase walletTransferUseCase,
                                    TopUpWalletUseCase topUpWalletUseCase,
                                    UserJpaRepository userJpaRepository,
                                    MerchantJpaRepository merchantJpaRepository) {
        this.getPaymentUseCase = getPaymentUseCase;
        this.processPaymentUseCase = processPaymentUseCase;
        this.refundPaymentUseCase = refundPaymentUseCase;
        this.listUserPaymentsUseCase = listUserPaymentsUseCase;
        this.searchPaymentsUseCase = searchPaymentsUseCase;
        this.getPaymentSummaryUseCase = getPaymentSummaryUseCase;
        this.processBatchPaymentsUseCase = processBatchPaymentsUseCase;
        this.walletTransferUseCase = walletTransferUseCase;
        this.topUpWalletUseCase = topUpWalletUseCase;
        this.userJpaRepository = userJpaRepository;
        this.merchantJpaRepository = merchantJpaRepository;
    }

    @QueryMapping
    public Payment payment(@Argument String id) {
        return getPaymentUseCase.execute(UUID.fromString(id))
                .orElse(null);
    }

    @QueryMapping
    public List<Payment> payments(@Argument String userId, @Argument String status, @Argument Integer limit) {
        return listUserPaymentsUseCase.execute(userId,
                status != null ? status : "SUCCESS",
                limit != null ? limit : 10);
    }

    @QueryMapping
    public List<Payment> searchPayments(@Argument Float minAmount, @Argument Float maxAmount,
                                         @Argument String currency, @Argument String status,
                                         @Argument Integer page, @Argument Integer size) {
        return searchPaymentsUseCase.execute(
                minAmount != null ? BigDecimal.valueOf(minAmount) : null,
                maxAmount != null ? BigDecimal.valueOf(maxAmount) : null,
                currency, status,
                page != null ? page : 0,
                size != null ? size : 10);
    }

    @QueryMapping
    public PaymentSummary paymentSummary(@Argument String startDate, @Argument String endDate) {
        return getPaymentSummaryUseCase.execute(
                Instant.parse(startDate), Instant.parse(endDate));
    }

    @MutationMapping
    public Payment processPayment(@Argument Map<String, Object> input) {
        UUID userId = UUID.fromString((String) input.get("userId"));
        UUID merchantId = UUID.fromString((String) input.get("merchantId"));
        BigDecimal amount = new BigDecimal(input.get("amount").toString());
        String type = input.get("type") != null ? (String) input.get("type") : "DEBIT";

        User user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Merchant merchant = merchantJpaRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        Payment payment = new Payment(UUID.randomUUID(), user, merchant, null,
                amount, type, "PENDING", Instant.now(), null);
        return processPaymentUseCase.execute(payment);
    }

    @MutationMapping
    public Payment walletTransfer(@Argument String walletId, @Argument String merchantId, @Argument Float amount) {
        return walletTransferUseCase.execute(
                UUID.fromString(walletId),
                UUID.fromString(merchantId),
                BigDecimal.valueOf(amount));
    }

    @MutationMapping
    public com.enterprise.payment.domain.model.Wallet topUpWallet(@Argument String walletId, @Argument Float amount) {
        return topUpWalletUseCase.execute(UUID.fromString(walletId), BigDecimal.valueOf(amount));
    }

    @MutationMapping
    public boolean processBatchPayments(@Argument List<Map<String, Object>> payments) {
        List<Payment> batch = payments.stream().map(p -> {
            UUID userId = UUID.fromString((String) p.get("userId"));
            UUID merchantId = UUID.fromString((String) p.get("merchantId"));
            BigDecimal amount = new BigDecimal(p.get("amount").toString());
            String type = p.get("type") != null ? (String) p.get("type") : "DEBIT";
            User user = userJpaRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            Merchant merchant = merchantJpaRepository.findById(merchantId)
                    .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
            return new Payment(UUID.randomUUID(), user, merchant, null, amount, type, "PENDING", Instant.now(), null);
        }).collect(Collectors.toList());
        processBatchPaymentsUseCase.execute(batch);
        return true;
    }

    @MutationMapping
    public Payment refundPayment(@Argument String id) {
        return refundPaymentUseCase.execute(UUID.fromString(id));
    }

    @SchemaMapping(typeName = "Payment", field = "userId")
    public String paymentUserId(Payment payment) {
        return payment.getUser() != null ? payment.getUser().getId().toString() : null;
    }

    @SchemaMapping(typeName = "Payment", field = "merchantId")
    public String paymentMerchantId(Payment payment) {
        return payment.getMerchant() != null ? payment.getMerchant().getId().toString() : null;
    }

    @SchemaMapping(typeName = "Payment", field = "walletId")
    public String paymentWalletId(Payment payment) {
        return payment.getWallet() != null ? payment.getWallet().getId().toString() : null;
    }

    @SchemaMapping(typeName = "PaymentSummary", field = "totalsByStatus")
    public List<Map<String, Object>> paymentSummaryTotals(PaymentSummary summary) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : summary.getTotalsByStatus().entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", entry.getKey());
            item.put("total", entry.getValue());
            result.add(item);
        }
        return result;
    }
}
