package com.enterprise.payment.infrastructure.persistence;

import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.domain.model.Wallet;
import com.enterprise.payment.infrastructure.persistence.jpa.MerchantJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.PaymentJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.WalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    @Autowired
    protected UserJpaRepository userJpaRepository;
    @Autowired
    protected MerchantJpaRepository merchantJpaRepository;
    @Autowired
    protected WalletJpaRepository walletJpaRepository;
    @Autowired
    protected PaymentJpaRepository paymentJpaRepository;

    protected User testUser;
    protected Merchant testMerchant;
    protected Wallet testWallet;

    @BeforeEach
    void seedData() {
        paymentJpaRepository.deleteAll();
        walletJpaRepository.deleteAll();
        merchantJpaRepository.deleteAll();
        userJpaRepository.deleteAll();

        testUser = new User(null, "matias@example.com", "Matias Magni", "ACTIVE");
        userJpaRepository.save(testUser);

        testMerchant = new Merchant(null, "Enterprise Store", "api-key-123");
        merchantJpaRepository.save(testMerchant);

        testWallet = new Wallet(null, testUser, new BigDecimal("1000.00"), "USD");
        walletJpaRepository.save(testWallet);
    }
}