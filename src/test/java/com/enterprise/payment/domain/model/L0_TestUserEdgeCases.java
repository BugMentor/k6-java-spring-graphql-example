package com.enterprise.payment.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class L0_TestUserEdgeCases {

    @Test
    @DisplayName("User with UUID constructor")
    void userWithUuid() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "test@edge.com", "Edge User", "ACTIVE");
        
        assertEquals(id, user.getId());
        assertEquals("test@edge.com", user.getEmail());
    }

    @Test
    @DisplayName("User INACTIVE status")
    void userInactiveStatus() {
        User user = new User(UUID.randomUUID(), "test@edge.com", "Test", "INACTIVE");
        
        assertEquals("INACTIVE", user.getStatus());
    }

    @Test
    @DisplayName("User SUSPENDED status")
    void userSuspendedStatus() {
        User user = new User(UUID.randomUUID(), "test@edge.com", "Test", "SUSPENDED");
        
        assertEquals("SUSPENDED", user.getStatus());
    }

    @Test
    @DisplayName("User with null status defaults to ACTIVE")
    void nullStatusDefaultsToActive() {
        User user = new User(UUID.randomUUID(), "test@edge.com", "Test", null);
        
        assertEquals("ACTIVE", user.getStatus());
    }

    @Test
    @DisplayName("User equals uses default object identity")
    void userEquality() {
        User u1 = new User(UUID.randomUUID(), "test1@edge.com", "User 1", "ACTIVE");
        User u2 = u1;
        
        assertEquals(u1, u2);
    }

    @Test
    @DisplayName("Different users are not equal")
    void differentUsersNotEqual() {
        User u1 = new User(UUID.randomUUID(), "test1@edge.com", "User 1", "ACTIVE");
        User u2 = new User(UUID.randomUUID(), "test2@edge.com", "User 2", "ACTIVE");
        
        assertNotEquals(u1, u2);
    }

    @Test
    @DisplayName("User toString is not null")
    void userToString() {
        User user = new User(UUID.randomUUID(), "test@edge.com", "Test User", "ACTIVE");
        
        assertNotNull(user.toString());
    }

    @Test
    @DisplayName("Very long full name")
    void veryLongFullName() {
        String longName = "A".repeat(500);
        User user = new User(UUID.randomUUID(), "test@edge.com", longName, "ACTIVE");
        
        assertEquals(500, user.getFullName().length());
    }

    @Test
    @DisplayName("Unicode email domain")
    void unicodeEmailDomain() {
        User user = new User(UUID.randomUUID(), "user@例子.cn", "User", "ACTIVE");
        
        assertEquals("user@例子.cn", user.getEmail());
    }

    @Test
    @DisplayName("Email with subdomain")
    void emailWithSubdomain() {
        User user = new User(UUID.randomUUID(), "user@mail.example.com", "User", "ACTIVE");
        
        assertEquals("mail.example.com", user.getEmail().split("@")[1]);
    }
}