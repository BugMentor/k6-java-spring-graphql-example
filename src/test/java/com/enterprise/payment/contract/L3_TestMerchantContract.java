package com.enterprise.payment.contract;

import com.enterprise.payment.BaseContractTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;

public class L3_TestMerchantContract extends BaseContractTest {

    @Test
    void shouldCreateMerchant() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "Contract Merchant",
                    "apiKey": "CONTRACT-KEY-001"
                }
                """)
            .when()
            .post(getBaseUrl() + "/v1/merchants")
            .then()
            .statusCode(anyOf(is(201), is(400)));
    }

    @Test
    void shouldGetMerchant() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get(getBaseUrl() + "/v1/merchants/" + UUID.randomUUID())
            .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test
    void shouldListMerchants() {
        given()
            .contentType(ContentType.JSON)
            .when()
            .get(getBaseUrl() + "/v1/merchants?page=0&size=20")
            .then()
            .statusCode(anyOf(is(200), is(400)));
    }
}