package com.enterprise.payment.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.enterprise.payment.domain.model.Merchant;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("L2 - Merchant Repository Integration (PostgreSQL)")
class L2_TestMerchantRepository extends AbstractPostgresDataJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private com.enterprise.payment.infrastructure.persistence.jpa.MerchantJpaRepository merchantRepository;

    @Test
    @DisplayName("Should save and retrieve merchant")
    void shouldSaveAndRetrieveMerchant() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "Test Store", "STORE-KEY-001");
        entityManager.persist(merchant);
        entityManager.flush();

        Optional<Merchant> found = merchantRepository.findById(merchant.getId());
        
        assertTrue(found.isPresent());
        assertEquals("Test Store", found.get().getName());
    }

    @Test
    @DisplayName("Should find all merchants")
    void shouldFindAllMerchants() {
        merchantRepository.save(new Merchant(UUID.randomUUID(), "Store 1", "KEY-1"));
        merchantRepository.save(new Merchant(UUID.randomUUID(), "Store 2", "KEY-2"));
        entityManager.flush();

        var all = merchantRepository.findAll();
        
        assertTrue(all.size() >= 2);
    }

    @Test
    @DisplayName("Should return empty for non-existent id")
    void shouldReturnEmptyForNonExistent() {
        Optional<Merchant> found = merchantRepository.findById(UUID.randomUUID());
        
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should delete merchant")
    void shouldDeleteMerchant() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "To Delete", "DEL-KEY");
        entityManager.persist(merchant);
        entityManager.flush();
        
        merchantRepository.delete(merchant);
        entityManager.flush();

        Optional<Merchant> found = merchantRepository.findById(merchant.getId());
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should persist merchant with apiKey")
    void shouldPersistWithApiKey() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "No Key Store", "NULLABLE-KEY");
        entityManager.persist(merchant);
        entityManager.flush();

        Optional<Merchant> found = merchantRepository.findById(merchant.getId());
        
        assertTrue(found.isPresent());
        assertEquals("NULLABLE-KEY", found.get().getApiKey());
    }

    @Test
    @DisplayName("Should handle duplicate names")
    void shouldHandleDuplicateNames() {
        Merchant m1 = new Merchant(UUID.randomUUID(), "Same Name", "KEY-1");
        Merchant m2 = new Merchant(UUID.randomUUID(), "Same Name", "KEY-2");
        
        entityManager.persist(m1);
        entityManager.persist(m2);
        entityManager.flush();

        var all = merchantRepository.findAll();
        assertTrue(all.stream().anyMatch(m -> "Same Name".equals(m.getName())));
    }
}
