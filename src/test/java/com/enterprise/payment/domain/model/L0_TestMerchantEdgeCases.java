package com.enterprise.payment.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class L0_TestMerchantEdgeCases {

    @Test
    @DisplayName("Merchant with UUID constructor")
    void merchantWithUuid() {
        UUID id = UUID.randomUUID();
        Merchant merchant = new Merchant(id, "Test Merchant", "API-KEY-123");
        
        assertEquals(id, merchant.getId());
        assertEquals("Test Merchant", merchant.getName());
        assertEquals("API-KEY-123", merchant.getApiKey());
    }

    @Test
    @DisplayName("Generate API key when null - returns null (no auto-generation)")
    void generateApiKeyWhenNull() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Test Merchant", null);
        
        assertNull(merchant.getApiKey());
    }

    @Test
    @DisplayName("Empty name is allowed (business decision)")
    void emptyNameAllowed() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "", "API-KEY");
        
        assertEquals("", merchant.getName());
    }

    @Test
    @DisplayName("Long merchant name")
    void longMerchantName() {
        String longName = "A".repeat(255);
        Merchant merchant = new Merchant(UUID.randomUUID(), longName, "API-KEY");
        
        assertEquals(255, merchant.getName().length());
    }

    @Test
    @DisplayName("Special characters in API key")
    void specialCharactersInApiKey() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Test", "API-KEY!@#$%^&*()");
        
        assertEquals("API-KEY!@#$%^&*()", merchant.getApiKey());
    }

    @Test
    @DisplayName("Merchant equals uses default object identity")
    void merchantEquality() {
        Merchant m1 = new Merchant(UUID.randomUUID(), "Merchant 1", "KEY-1");
        Merchant m2 = m1;
        
        assertEquals(m1, m2);
    }

    @Test
    @DisplayName("Different merchants are not equal")
    void differentMerchantsNotEqual() {
        Merchant m1 = new Merchant(UUID.randomUUID(), "Merchant 1", "KEY-1");
        Merchant m2 = new Merchant(UUID.randomUUID(), "Merchant 1", "KEY-1");
        
        assertNotEquals(m1, m2);
    }

    @Test
    @DisplayName("Merchant toString is not null")
    void merchantToString() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Test", "KEY-123");
        String str = merchant.toString();
        
        assertNotNull(str);
    }

    @Test
    @DisplayName("Merchant with very long API key")
    void veryLongApiKey() {
        String longKey = "A".repeat(500);
        Merchant merchant = new Merchant(UUID.randomUUID(), "Test", longKey);
        
        assertEquals(500, merchant.getApiKey().length());
    }

    @Test
    @DisplayName("Merchant with Unicode characters in name")
    void unicodeInName() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Магазин 商店", "KEY");
        
        assertEquals("Магазин 商店", merchant.getName());
    }
}