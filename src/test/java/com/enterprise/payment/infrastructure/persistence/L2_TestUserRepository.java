package com.enterprise.payment.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.enterprise.payment.domain.model.User;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("L2 - User Repository Integration (PostgreSQL)")
class L2_TestUserRepository extends AbstractPostgresDataJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private com.enterprise.payment.infrastructure.persistence.jpa.UserJpaRepository userRepository;

    @Test
    @DisplayName("Should save and retrieve user")
    void shouldSaveAndRetrieveUser() {
        User user = new User(UUID.randomUUID(), "test@postgres.com", "PG Test User", "ACTIVE");
        entityManager.persist(user);
        entityManager.flush();

        Optional<User> found = userRepository.findById(user.getId());
        
        assertTrue(found.isPresent());
        assertEquals("test@postgres.com", found.get().getEmail());
    }

    @Test
    @DisplayName("Should find by email")
    void shouldFindByEmail() {
        User user = new User(UUID.randomUUID(), "search@postgres.com", "Search User", "ACTIVE");
        entityManager.persist(user);
        entityManager.flush();

        Optional<User> found = userRepository.findById(user.getId());
        
        assertTrue(found.isPresent());
        assertEquals("Search User", found.get().getFullName());
    }

    @Test
    @DisplayName("Should return empty for non-existent id")
    void shouldReturnEmptyForNonExistent() {
        Optional<User> found = userRepository.findById(UUID.randomUUID());
        
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should delete user")
    void shouldDeleteUser() {
        User user = new User(UUID.randomUUID(), "delete@postgres.com", "Delete User", "ACTIVE");
        entityManager.persist(user);
        entityManager.flush();
        
        userRepository.delete(user);
        entityManager.flush();

        Optional<User> found = userRepository.findById(user.getId());
        assertFalse(found.isPresent());
    }
}
