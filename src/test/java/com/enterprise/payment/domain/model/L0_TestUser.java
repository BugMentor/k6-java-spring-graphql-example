package com.enterprise.payment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User Domain Entity Tests")
class L0_TestUser {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create User with all valid parameters")
        void shouldCreateUserWithValidParameters() {
            UUID id = UUID.randomUUID();
            String email = "test@example.com";
            String fullName = "John Doe";
            String status = "ACTIVE";

            User user = new User(id, email, fullName, status);

            assertEquals(id, user.getId());
            assertEquals(email, user.getEmail());
            assertEquals(fullName, user.getFullName());
            assertEquals("ACTIVE", user.getStatus());
        }

        @Test
        @DisplayName("Should generate UUID when null is provided")
        void shouldGenerateUuidWhenNull() {
            User user = new User(null, "test@example.com", "John Doe", "ACTIVE");

            assertNotNull(user.getId());
        }

        @Test
        @DisplayName("Should default status to ACTIVE when null is provided")
        void shouldDefaultStatusToActive() {
            User user = new User(UUID.randomUUID(), "test@example.com", "John Doe", null);

            assertEquals("ACTIVE", user.getStatus());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException for null email")
        void shouldThrowForNullEmail() {
            assertThrows(IllegalArgumentException.class, () ->
                new User(UUID.randomUUID(), null, "John Doe", "ACTIVE")
            );
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty email")
        void shouldThrowForEmptyEmail() {
            assertThrows(IllegalArgumentException.class, () ->
                new User(UUID.randomUUID(), "", "John Doe", "ACTIVE")
            );
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid email format 1")
        void shouldThrowForInvalidEmailFormat1() {
            assertThrows(IllegalArgumentException.class, () ->
                new User(UUID.randomUUID(), "not-an-email", "John Doe", "ACTIVE")
            );
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid email format 2")
        void shouldThrowForInvalidEmailFormat2() {
            assertThrows(IllegalArgumentException.class, () ->
                new User(UUID.randomUUID(), "no-at-sign.com", "John Doe", "ACTIVE")
            );
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid email format 3")
        void shouldThrowForInvalidEmailFormat3() {
            assertThrows(IllegalArgumentException.class, () ->
                new User(UUID.randomUUID(), "invalid-email", "John Doe", "ACTIVE")
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should throw IllegalArgumentException for null or empty fullName")
        void shouldThrowForNullOrEmptyFullName(String invalidName) {
            assertThrows(IllegalArgumentException.class, () ->
                new User(UUID.randomUUID(), "test@example.com", invalidName, "ACTIVE")
            );
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank fullName")
        void shouldThrowForBlankFullName() {
            assertThrows(IllegalArgumentException.class, () ->
                new User(UUID.randomUUID(), "test@example.com", "   ", "ACTIVE")
            );
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should accept email with subdomain")
        void shouldAcceptEmailWithSubdomain() {
            User user = new User(UUID.randomUUID(), "user@mail.example.com", "John", "ACTIVE");
            assertNotNull(user);
        }

        @Test
        @DisplayName("Should accept email with plus sign")
        void shouldAcceptEmailWithPlusSign() {
            User user = new User(UUID.randomUUID(), "user+tag@example.com", "John", "ACTIVE");
            assertNotNull(user);
        }

        @Test
        @DisplayName("Should accept any status value")
        void shouldAcceptAnyStatusValue() {
            User user = new User(UUID.randomUUID(), "test@example.com", "John", "SUSPENDED");
            assertEquals("SUSPENDED", user.getStatus());
        }

        @Test
        @DisplayName("Should allow unicode in fullName")
        void shouldAllowUnicodeInFullName() {
            User user = new User(UUID.randomUUID(), "test@example.com", "José García", "ACTIVE");
            assertEquals("José García", user.getFullName());
        }
    }
}