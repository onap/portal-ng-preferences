/*
 *
 * Copyright (c) 2022. Deutsche Telekom AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *
 */

package org.onap.portalng.preferences.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.onap.portalng.preferences.BaseIntegrationTest;
import org.onap.portalng.preferences.openapi.model.Preferences;
import org.onap.portalng.preferences.services.PreferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import io.restassured.http.ContentType;
import io.restassured.http.Header;

class PreferencesControllerIntegrationTest extends BaseIntegrationTest {

    protected static final String X_REQUEST_ID = "addf6005-3075-4c80-b7bc-2c70b7d42b57";

    @Autowired
    PreferencesService preferencesService;

    @Test
    void thatUserPreferencesCanBeRetrieved() {
        // First save a user preference before a GET can run
        Preferences expectedResponse = new Preferences()
            .properties("{\"properties\": {\"dashboard\": {\"key1:\": \"value2\"}, \"appStarter\": \"value1\"}}");
        preferencesService
            .savePreferences(X_REQUEST_ID,"test-user", expectedResponse)
            .block();

        Preferences actualResponse = requestSpecification("test-user")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", X_REQUEST_ID))
            .when()
            .get("/v1/preferences")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(Preferences.class);

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getProperties()).isEqualTo(expectedResponse.getProperties());
    }

    @Test
    void thatUserPreferencesCanNotBeRetrieved() {
        unauthenticatedRequestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(ContentType.JSON)
            .header(new Header("X-Request-Id", X_REQUEST_ID))
            .when()
            .get("/v1/preferences")
            .then()
            .statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void thatUserPreferencesCanBeSaved() {
        Preferences expectedResponse = new Preferences()
            .properties("{\n" +
                "    \"properties\": { \"appStarter\": \"value1\",\n" +
                "    \"dashboard\": {\"key1:\" : \"value2\"}\n" +
                "    } \n" +
                "}");
        Preferences actualResponse = requestSpecification()
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(ContentType.JSON)
            .header(new Header("X-Request-Id", X_REQUEST_ID))
            .body(expectedResponse)
            .when()
            .post("/v1/preferences")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(Preferences.class);

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getProperties()).isEqualTo(expectedResponse.getProperties());
    }

    @Test
    void thatUserPreferencesCanBeUpdated() {
        // First save a user preference before a GET can run
        Preferences initialPreferences = new Preferences()
            .properties("{\n" +
                "    \"properties\": { \"appStarter\": \"value1\",\n" +
                "    \"dashboard\": {\"key1:\" : \"value2\"}\n" +
                "    } \n" +
                "}");
        preferencesService
            .savePreferences(X_REQUEST_ID,"test-user", initialPreferences)
            .block();

        Preferences expectedResponse = new Preferences()
            .properties("{\n" +
                "    \"properties\": { \"appStarter\": \"value3\",\n" +
                "    \"dashboard\": {\"key2:\" : \"value4\"}\n" +
                "    } \n" +
                "}");
        Preferences actualResponse = requestSpecification("test-user")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .contentType(ContentType.JSON)
            .header(new Header("X-Request-Id", X_REQUEST_ID))
            .body(expectedResponse)
            .when()
            .put("/v1/preferences")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(Preferences.class);

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getProperties()).isEqualTo(expectedResponse.getProperties());
    }

    @Test
    void thatUserPreferencesCanNotBeFound() {

        Preferences actualResponse = requestSpecification("test-canNotBeFound")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", X_REQUEST_ID))
            .when()
            .get("/v1/preferences")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .body()
            .as(Preferences.class);

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getProperties()).isNull();
    }

    @Test
    void thatUserPreferencesHasXRequestIdHeader() {

        String actualResponse = requestSpecification("test-user")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .header(new Header("X-Request-Id", X_REQUEST_ID))
            .when()
            .get("/v1/preferences")
            .then()
            .statusCode(HttpStatus.OK.value())
            .extract()
            .header("X-Request-Id");

        assertThat(actualResponse).isNotNull().isEqualTo(X_REQUEST_ID);
    }

    @Test
    void thatUserPreferencesHasNoXRequestIdHeader() {

         requestSpecification("test-user")
            .given()
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .when()
            .get("/v1/preferences")
            .then()
            .statusCode(HttpStatus.BAD_REQUEST.value());


    }
}
