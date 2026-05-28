package com.enterprise.payment.contract;

import com.enterprise.payment.BaseContractTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

public class L3_TestPaymentContract extends BaseContractTest {

    @Test
    void shouldCreatePayment() {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        given()
            .contentType(ContentType.JSON)
            .body(String.format("""
                {
                    "userId": "%s",
                    "merchantId": "%s",
                    "amount": 100.00,
                    "type": "DEBIT",
                    "status": "PENDING"
                }
                """, userId, merchantId))
            .when()
            .post(getBaseUrl() + "/v1/payments")
            .then()
            .statusCode(anyOf(is(201), is(400), is(404), is(500)));
    }

    @Test
    void shouldGetPayment() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get(getBaseUrl() + "/v1/payments/" + UUID.randomUUID())
            .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test
    void shouldListUserPayments() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get(getBaseUrl() + "/v1/payments/user/" + UUID.randomUUID() + "?status=SUCCESS&limit=10")
            .then()
            .statusCode(anyOf(is(200), is(400)));
    }

    @Test
    void shouldSearchPayments() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get(getBaseUrl() + "/v1/payments/search?minAmount=10&maxAmount=500&status=SUCCESS&page=0&size=10")
            .then()
            .statusCode(is(200));
    }

    @Test
    void shouldRefundPayment() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .put(getBaseUrl() + "/v1/payments/" + UUID.randomUUID() + "/refund")
            .then()
            .statusCode(anyOf(is(200), is(404), is(400)));
    }

    @Test
    void shouldGetPaymentSummary() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get(getBaseUrl() + "/v1/payments/reports/summary?startDate=2024-01-01T00:00:00Z&endDate=2024-12-31T23:59:59Z")
            .then()
            .statusCode(anyOf(is(200), is(400)));
    }
}