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
import org.onap.portalng.preferences.openapi.model.PreferencesApiDto;
import org.onap.portalng.preferences.services.PreferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import io.restassured.http.ContentType;

class PreferencesControllerIntegrationTest extends BaseIntegrationTest {

  @Autowired
  PreferencesService preferencesService;

  @Test
  void thatUserPreferencesCanBeRetrieved() {
    // First save a user preference before a GET can run
    PreferencesApiDto expectedResponse = new PreferencesApiDto()
        .properties("{\"properties\": {\"dashboard\": {\"key1:\": \"value2\"}, \"appStarter\": \"value1\"}}");
    preferencesService
        .savePreferences("test-user", expectedResponse)
        .block();

    PreferencesApiDto actualResponse = requestSpecification("test-user")
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .when()
        .get("/v1/preferences")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(PreferencesApiDto.class);

    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.getProperties()).isEqualTo(expectedResponse.getProperties());
  }

  @Test
  void thatUserPreferencesCanNotBeRetrieved() {
    unauthenticatedRequestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .contentType(ContentType.JSON)
        .when()
        .get("/v1/preferences")
        .then()
        .statusCode(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void thatUserPreferencesCanBeSaved() {
    PreferencesApiDto expectedResponse = new PreferencesApiDto()
        .properties("""
            {
                "properties": { "appStarter": "value1",
                "dashboard": {"key1:" : "value2"}
                }\s
            }\
            """);
    PreferencesApiDto actualResponse = requestSpecification()
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .contentType(ContentType.JSON)
        .body(expectedResponse)
        .when()
        .post("/v1/preferences")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(PreferencesApiDto.class);

    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.getProperties()).isEqualTo(expectedResponse.getProperties());
  }

  @Test
  void thatUserPreferencesCanBeUpdated() {
    // First save a user preference before a GET can run
    PreferencesApiDto initialPreferences = new PreferencesApiDto()
        .properties("""
            {
                "properties": { "appStarter": "value1",
                "dashboard": {"key1:" : "value2"}
                }\s
            }\
            """);
    preferencesService
        .savePreferences("test-user", initialPreferences)
        .block();

    PreferencesApiDto expectedResponse = new PreferencesApiDto()
        .properties("""
            {
                "properties": { "appStarter": "value3",
                "dashboard": {"key2:" : "value4"}
                }\s
            }\
            """);
    PreferencesApiDto actualResponse = requestSpecification("test-user")
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .contentType(ContentType.JSON)
        .body(expectedResponse)
        .when()
        .put("/v1/preferences")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(PreferencesApiDto.class);

    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.getProperties()).isEqualTo(expectedResponse.getProperties());
  }

  @Test
  void thatUserPreferencesCanNotBeFound() {

    PreferencesApiDto actualResponse = requestSpecification("test-canNotBeFound")
        .given()
        .accept(MediaType.APPLICATION_JSON_VALUE)
        .when()
        .get("/v1/preferences")
        .then()
        .statusCode(HttpStatus.OK.value())
        .extract()
        .body()
        .as(PreferencesApiDto.class);

    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.getProperties()).isNull();
  }
}
