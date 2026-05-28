package com.enterprise.payment;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

import com.enterprise.payment.application.usecase.*;
import com.enterprise.payment.infrastructure.persistence.jpa.*;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseContractTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserJpaRepository userRepository;

    @Autowired
    protected MerchantJpaRepository merchantRepository;

    @Autowired
    protected WalletJpaRepository walletRepository;

    @Autowired
    protected PaymentJpaRepository paymentRepository;

    @Autowired
    protected ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    protected GetPaymentUseCase getPaymentUseCase;

    @Autowired
    protected RefundPaymentUseCase refundPaymentUseCase;

    @Autowired
    protected TopUpWalletUseCase topUpWalletUseCase;

    @Autowired
    protected WalletTransferUseCase walletTransferUseCase;

    @Autowired
    protected ProcessBatchPaymentsUseCase processBatchPaymentsUseCase;

    @Autowired
    protected GetPaymentSummaryUseCase getPaymentSummaryUseCase;

    @Autowired
    protected ListUserPaymentsUseCase listUserPaymentsUseCase;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.webAppContextSetup(context);
        paymentRepository.deleteAll();
        walletRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }
}