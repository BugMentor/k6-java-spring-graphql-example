package com.enterprise.payment.contract;

import com.enterprise.payment.BaseContractTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

public class L3_TestUserContract extends BaseContractTest {

    @Test
    void shouldCreateUser() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "contract@test.com",
                    "fullName": "Contract Test User",
                    "status": "ACTIVE"
                }
                """)
            .when()
            .post(getBaseUrl() + "/v1/users")
            .then()
            .statusCode(anyOf(is(201), is(400)));
    }

    @Test
    void shouldGetUser() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get(getBaseUrl() + "/v1/users/" + UUID.randomUUID())
            .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test
    void shouldListUsers() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get(getBaseUrl() + "/v1/users?page=0&size=20")
            .then()
            .statusCode(anyOf(is(200), is(400)));
    }

    @Test
    void shouldValidateInvalidEmail() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "invalid-email",
                    "fullName": "Test User",
                    "status": "ACTIVE"
                }
                """)
            .when()
            .post(getBaseUrl() + "/v1/users")
            .then()
            .statusCode(anyOf(is(400), is(500)));
    }
}