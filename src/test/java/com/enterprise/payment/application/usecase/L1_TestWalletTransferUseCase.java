package com.enterprise.payment.application.usecase;

import com.enterprise.payment.application.port.outgoing.PaymentRepositoryPort;
import com.enterprise.payment.domain.model.Merchant;
import com.enterprise.payment.domain.model.Payment;
import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.domain.model.Wallet;
import com.enterprise.payment.infrastructure.persistence.jpa.MerchantJpaRepository;
import com.enterprise.payment.infrastructure.persistence.jpa.WalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WalletTransferUseCase Tests")
class L1_TestWalletTransferUseCase {

    @Mock
    private WalletJpaRepository walletJpaRepository;

    @Mock
    private MerchantJpaRepository merchantJpaRepository;

    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;

    private WalletTransferUseCase walletTransferUseCase;

    private User testUser;
    private Merchant testMerchant;
    private Wallet testWallet;
    private UUID walletId;
    private UUID merchantId;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        walletTransferUseCase = new WalletTransferUseCase(
            walletJpaRepository,
            merchantJpaRepository,
            paymentRepositoryPort
        );

        testUser = new User(UUID.randomUUID(), "user@test.com", "Test User", "ACTIVE");
        testMerchant = new Merchant(UUID.randomUUID(), "Test Merchant", "MERCH-001");
        testWallet = new Wallet(UUID.randomUUID(), testUser, new BigDecimal("1000.00"), "USD");

        walletId = testWallet.getId();
        merchantId = testMerchant.getId();
        amount = new BigDecimal("100.00");
    }

    @Nested
    @DisplayName("Execute Method Tests")
    class ExecuteTests {

        @Test
        @DisplayName("Should transfer successfully when sufficient funds")
        void shouldTransferSuccessfully() {
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(merchantJpaRepository.findById(merchantId)).thenReturn(Optional.of(testMerchant));
            when(walletJpaRepository.save(any(Wallet.class))).thenReturn(testWallet);

            Payment expectedPayment = new Payment(
                UUID.randomUUID(),
                testUser,
                testMerchant,
                testWallet,
                amount,
                "WALLET_TRANSFER",
                "SUCCESS",
                Instant.now(),
                1L
            );
            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(expectedPayment);

            Payment result = walletTransferUseCase.execute(walletId, merchantId, amount);

            assertNotNull(result);
            assertEquals("SUCCESS", result.getStatus());
            verify(walletJpaRepository).findByIdWithLock(walletId);
            verify(walletJpaRepository).save(any(Wallet.class));
            verify(paymentRepositoryPort).save(any(Payment.class));
        }

        @Test
        @DisplayName("Should throw when wallet not found")
        void shouldThrowWhenWalletNotFound() {
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> walletTransferUseCase.execute(walletId, merchantId, amount)
            );

            assertEquals("Wallet not found", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw when merchant not found")
        void shouldThrowWhenMerchantNotFound() {
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(merchantJpaRepository.findById(merchantId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> walletTransferUseCase.execute(walletId, merchantId, amount)
            );

            assertEquals("Merchant not found", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw when insufficient funds")
        void shouldThrowWhenInsufficientFunds() {
            BigDecimal largeAmount = new BigDecimal("5000.00");
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(merchantJpaRepository.findById(merchantId)).thenReturn(Optional.of(testMerchant));

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> walletTransferUseCase.execute(walletId, merchantId, largeAmount)
            );

            assertTrue(exception.getMessage().contains("Insufficient funds"));
        }

        @Test
        @DisplayName("Should update wallet balance after transfer")
        void shouldUpdateWalletBalance() {
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(merchantJpaRepository.findById(merchantId)).thenReturn(Optional.of(testMerchant));
            when(walletJpaRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Payment expectedPayment = new Payment(
                UUID.randomUUID(),
                testUser,
                testMerchant,
                testWallet,
                amount,
                "WALLET_TRANSFER",
                "SUCCESS",
                Instant.now(),
                1L
            );
            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(expectedPayment);

            walletTransferUseCase.execute(walletId, merchantId, amount);

            // Verify wallet was debited (balance should be 1000 - 100 = 900)
            verify(walletJpaRepository).save(argThat(wallet ->
                wallet.getBalance().equals(new BigDecimal("900.00"))
            ));
        }
    }

    @Nested
    @DisplayName("Pessimistic Locking Verification")
    class PessimisticLockingTests {

        @Test
        @DisplayName("Should call findByIdWithLock (pessimistic lock)")
        void shouldCallFindByIdWithLock() {
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(merchantJpaRepository.findById(merchantId)).thenReturn(Optional.of(testMerchant));
            when(walletJpaRepository.save(any(Wallet.class))).thenReturn(testWallet);

            Payment expectedPayment = new Payment(
                UUID.randomUUID(),
                testUser,
                testMerchant,
                testWallet,
                amount,
                "WALLET_TRANSFER",
                "SUCCESS",
                Instant.now(),
                1L
            );
            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(expectedPayment);

            walletTransferUseCase.execute(walletId, merchantId, amount);

            verify(walletJpaRepository).findByIdWithLock(walletId);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle exact balance transfer")
        void shouldHandleExactBalanceTransfer() {
            BigDecimal exactAmount = new BigDecimal("1000.00");
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(merchantJpaRepository.findById(merchantId)).thenReturn(Optional.of(testMerchant));
            when(walletJpaRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Payment expectedPayment = new Payment(
                UUID.randomUUID(),
                testUser,
                testMerchant,
                testWallet,
                exactAmount,
                "WALLET_TRANSFER",
                "SUCCESS",
                Instant.now(),
                1L
            );
            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(expectedPayment);

            Payment result = walletTransferUseCase.execute(walletId, merchantId, exactAmount);

            assertNotNull(result);
            // Balance should become zero - just verify save was called
            verify(walletJpaRepository).save(any(Wallet.class));
        }

        @Test
        @DisplayName("Should handle very small transfer amount")
        void shouldHandleSmallAmount() {
            BigDecimal smallAmount = new BigDecimal("0.01");
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(merchantJpaRepository.findById(merchantId)).thenReturn(Optional.of(testMerchant));
            when(walletJpaRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Payment expectedPayment = new Payment(
                UUID.randomUUID(),
                testUser,
                testMerchant,
                testWallet,
                smallAmount,
                "WALLET_TRANSFER",
                "SUCCESS",
                Instant.now(),
                1L
            );
            when(paymentRepositoryPort.save(any(Payment.class))).thenReturn(expectedPayment);

            Payment result = walletTransferUseCase.execute(walletId, merchantId, smallAmount);

            assertNotNull(result);
        }
    }
}