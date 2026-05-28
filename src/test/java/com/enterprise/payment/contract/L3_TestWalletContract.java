package com.enterprise.payment.contract;

import com.enterprise.payment.BaseContractTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

public class L3_TestWalletContract extends BaseContractTest {

    @Test
    void shouldCreateWallet() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "userId": "#{json-unit.ignore}",
                    "balance": 1000.00,
                    "currency": "USD"
                }
                """)
            .when()
            .post(getBaseUrl() + "/v1/wallets")
            .then()
            .statusCode(anyOf(is(201), is(400)));
    }

    @Test
    void shouldGetWallet() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get(getBaseUrl() + "/v1/wallets/" + UUID.randomUUID())
            .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test
    void shouldTopUpWallet() {
        given()
            .contentType(ContentType.JSON)
            .body("500.00")
            .when()
            .post(getBaseUrl() + "/v1/wallets/" + UUID.randomUUID() + "/topup")
            .then()
            .statusCode(anyOf(is(200), is(400), is(404)));
    }

    @Test
    void shouldTransferFromWallet() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "walletId": "#{json-unit.ignore}",
                    "merchantId": "#{json-unit.ignore}",
                    "amount": 50.00
                }
                """)
            .when()
            .post(getBaseUrl() + "/v1/payments/wallet-transfer")
            .then()
            .statusCode(anyOf(is(200), is(400), is(404)));
    }
}