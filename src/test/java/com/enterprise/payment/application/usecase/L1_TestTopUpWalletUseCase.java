package com.enterprise.payment.application.usecase;

import com.enterprise.payment.domain.model.User;
import com.enterprise.payment.domain.model.Wallet;
import com.enterprise.payment.infrastructure.persistence.jpa.WalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TopUpWalletUseCase Tests")
class L1_TestTopUpWalletUseCase {

    @Mock
    private WalletJpaRepository walletJpaRepository;

    private TopUpWalletUseCase topUpWalletUseCase;

    private User testUser;
    private Wallet testWallet;
    private UUID walletId;

    @BeforeEach
    void setUp() {
        topUpWalletUseCase = new TopUpWalletUseCase(walletJpaRepository);

        testUser = new User(UUID.randomUUID(), "user@test.com", "Test User", "ACTIVE");
        testWallet = new Wallet(UUID.randomUUID(), testUser, new BigDecimal("100.00"), "USD");
        walletId = testWallet.getId();
    }

    @Nested
    @DisplayName("Execute Method Tests")
    class ExecuteTests {

        @Test
        @DisplayName("Should top-up wallet successfully")
        void shouldTopUpSuccessfully() {
            BigDecimal topUpAmount = new BigDecimal("50.00");
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(walletJpaRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Wallet result = topUpWalletUseCase.execute(walletId, topUpAmount);

            assertNotNull(result);
            assertEquals(new BigDecimal("150.00"), result.getBalance());
            verify(walletJpaRepository).findByIdWithLock(walletId);
            verify(walletJpaRepository).save(any(Wallet.class));
        }

        @Test
        @DisplayName("Should throw when wallet not found")
        void shouldThrowWhenWalletNotFound() {
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> topUpWalletUseCase.execute(walletId, new BigDecimal("50.00"))
            );

            assertEquals("Wallet not found", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw for zero top-up amount")
        void shouldThrowForZeroAmount() {
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> topUpWalletUseCase.execute(walletId, BigDecimal.ZERO)
            );

            assertTrue(exception.getMessage().contains("positive"));
        }

        @Test
        @DisplayName("Should throw for negative top-up amount")
        void shouldThrowForNegativeAmount() {
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));

            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> topUpWalletUseCase.execute(walletId, new BigDecimal("-50.00"))
            );

            assertTrue(exception.getMessage().contains("positive"));
        }

        @Test
        @DisplayName("Should update balance correctly")
        void shouldUpdateBalanceCorrectly() {
            BigDecimal topUpAmount = new BigDecimal("250.00");
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(walletJpaRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Wallet result = topUpWalletUseCase.execute(walletId, topUpAmount);

            // Original balance 100 + 250 = 350
            assertEquals(new BigDecimal("350.00"), result.getBalance());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle large top-up amount")
        void shouldHandleLargeAmount() {
            BigDecimal largeAmount = new BigDecimal("999999.99");
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(walletJpaRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Wallet result = topUpWalletUseCase.execute(walletId, largeAmount);

            assertEquals(new BigDecimal("1000099.99"), result.getBalance());
        }

        @Test
        @DisplayName("Should handle very small top-up amount")
        void shouldHandleSmallAmount() {
            BigDecimal smallAmount = new BigDecimal("0.01");
            when(walletJpaRepository.findByIdWithLock(walletId)).thenReturn(Optional.of(testWallet));
            when(walletJpaRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Wallet result = topUpWalletUseCase.execute(walletId, smallAmount);

            assertEquals(new BigDecimal("100.01"), result.getBalance());
        }
    }
}