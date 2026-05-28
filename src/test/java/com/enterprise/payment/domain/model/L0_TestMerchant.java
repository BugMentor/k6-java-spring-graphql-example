package com.enterprise.payment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Merchant Domain Entity Tests")
class L0_TestMerchant {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create Merchant with all valid parameters")
        void shouldCreateMerchantWithValidParameters() {
            UUID id = UUID.randomUUID();
            String name = "Tech Corp";
            String apiKey = "MERCH-001-KEY";

            Merchant merchant = new Merchant(id, name, apiKey);

            assertEquals(id, merchant.getId());
            assertEquals(name, merchant.getName());
            assertEquals(apiKey, merchant.getApiKey());
        }

        @Test
        @DisplayName("Should generate UUID when null is provided")
        void shouldGenerateUuidWhenNull() {
            Merchant merchant = new Merchant(null, "Tech Corp", "API_KEY");

            assertNotNull(merchant.getId());
        }

        @Test
        @DisplayName("Should accept empty name (nullable=false but no validation)")
        void shouldAcceptEmptyName() {
            Merchant merchant = new Merchant(UUID.randomUUID(), "", "API_KEY");

            assertEquals("", merchant.getName());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should accept special characters in name")
        void shouldAcceptSpecialCharactersInName() {
            Merchant merchant = new Merchant(UUID.randomUUID(), "Tech & Sons #1", "API_KEY");

            assertEquals("Tech & Sons #1", merchant.getName());
        }

        @Test
        @DisplayName("Should accept long API key")
        void shouldAcceptLongApiKey() {
            String longKey = "A".repeat(256);
            Merchant merchant = new Merchant(UUID.randomUUID(), "Test", longKey);

            assertEquals(longKey, merchant.getApiKey());
        }

        @Test
        @DisplayName("Should accept unicode characters in name")
        void shouldAcceptUnicodeInName() {
            Merchant merchant = new Merchant(UUID.randomUUID(), "Техно Корп", "API_KEY");

            assertEquals("Техно Корп", merchant.getName());
        }
    }
}