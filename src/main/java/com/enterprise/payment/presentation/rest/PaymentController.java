package com.enterprise.payment.presentation.rest;

import com.enterprise.payment.application.usecase.*;
import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.PaymentSummary;
import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.domain.model.Wallet;
import com.enterprise.payment.infrastructure.persistence.jpa.MerchantJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final ProcessBatchPaymentsUseCase processBatchPaymentsUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final ListUserPaymentsUseCase listUserPaymentsUseCase;
    private final GetPaymentSummaryUseCase getPaymentSummaryUseCase;
    private final SearchPaymentsUseCase searchPaymentsUseCase;
    private final WalletTransferUseCase walletTransferUseCase;
    private final TopUpWalletUseCase topUpWalletUseCase;
    private final UserJpaRepository userJpaRepository;
    private final MerchantJpaRepository merchantJpaRepository;

    public PaymentController(ProcessPaymentUseCase processPaymentUseCase,
                             ProcessBatchPaymentsUseCase processBatchPaymentsUseCase,
                             RefundPaymentUseCase refundPaymentUseCase,
                             GetPaymentUseCase getPaymentUseCase,
                             ListUserPaymentsUseCase listUserPaymentsUseCase,
                             GetPaymentSummaryUseCase getPaymentSummaryUseCase,
                             SearchPaymentsUseCase searchPaymentsUseCase,
                             WalletTransferUseCase walletTransferUseCase,
                             TopUpWalletUseCase topUpWalletUseCase,
                             UserJpaRepository userJpaRepository,
                             MerchantJpaRepository merchantJpaRepository) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.processBatchPaymentsUseCase = processBatchPaymentsUseCase;
        this.refundPaymentUseCase = refundPaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
        this.listUserPaymentsUseCase = listUserPaymentsUseCase;
        this.getPaymentSummaryUseCase = getPaymentSummaryUseCase;
        this.searchPaymentsUseCase = searchPaymentsUseCase;
        this.walletTransferUseCase = walletTransferUseCase;
        this.topUpWalletUseCase = topUpWalletUseCase;
        this.userJpaRepository = userJpaRepository;
        this.merchantJpaRepository = merchantJpaRepository;
    }

    public record CreatePaymentRequest(UUID userId, UUID merchantId, BigDecimal amount, String type, String status) {}

    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody CreatePaymentRequest request) {
        User user = userJpaRepository.findById(request.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.userId()));
        Merchant merchant = merchantJpaRepository.findById(request.merchantId())
            .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + request.merchantId()));
        Payment payment = new Payment(
            UUID.randomUUID(),
            user,
            merchant,
            null,
            request.amount(),
            request.type() != null ? request.type() : "DEBIT",
            request.status() != null ? request.status() : "PENDING",
            Instant.now(),
            null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(processPaymentUseCase.execute(payment));
    }

    @PostMapping("/wallet-transfer")
    public ResponseEntity<Payment> walletTransfer(@RequestBody WalletTransferRequest request) {
        return ResponseEntity.ok(walletTransferUseCase.execute(request.walletId, request.merchantId, request.amount));
    }

    @PostMapping("/wallets/{id}/topup")
    public ResponseEntity<Wallet> topUp(@PathVariable UUID id, @RequestBody BigDecimal amount) {
        return ResponseEntity.ok(topUpWalletUseCase.execute(id, amount));
    }

    public static record WalletTransferRequest(UUID walletId, UUID merchantId, BigDecimal amount) {}

    @PostMapping("/batch")
    @WithSpan("rest-batch-payments")
    public ResponseEntity<Void> createBatch(@RequestBody List<Payment> payments) {
        processBatchPaymentsUseCase.execute(payments);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<Payment> refund(@PathVariable UUID id) {
        return ResponseEntity.ok(refundPaymentUseCase.execute(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPayment(@PathVariable UUID id) {
        return getPaymentUseCase.execute(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Payment>> listUserPayments(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "SUCCESS") String status,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ResponseEntity.ok(listUserPaymentsUseCase.execute(userId, status, limit));
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<PaymentSummary> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return ResponseEntity.ok(getPaymentSummaryUseCase.execute(startDate, endDate));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Payment>> search(
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(searchPaymentsUseCase.execute(minAmount, maxAmount, type, status, page, size));
    }
}
