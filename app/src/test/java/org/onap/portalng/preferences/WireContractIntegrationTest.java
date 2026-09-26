/*
 *
 * Copyright (c) 2026. Deutsche Telekom AG
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

package org.onap.portalng.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.onap.portalng.preferences.repository.PreferencesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Pins the raw bytes this service puts on the wire, so that a change of the JSON library or its
 * defaults cannot silently alter what bff and portal-ui receive. Assertions compare raw strings
 * rather than parsed trees on purpose: key order, number formatting and null handling are part of
 * the contract.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WireContractIntegrationTest {

  private static final String PATH = "/v1/preferences";

  private static final String ARBITRARY_JSON =
      "{\"properties\":{\"zeta\":1,\"alpha\":{\"b\":[1,2.5,\"x\",true,null,{}],"
          + "\"a\":\"\\u00e4\\u2603\"},\"big\":12345678901234567890123,\"f\":0.1,\"e\":1e300,"
          + "\"d\":1.0,\"neg\":-0.0,\"long\":9007199254740993,\"nul\":null,\"empty\":{},"
          + "\"arr\":[],\"s\":\"2026-09-25T10:00:00Z\"}}";

  // Boot's DefaultErrorWebExceptionHandler body, rendered through the JSON codec.
  private static final Pattern DEFAULT_ERROR_BODY =
      Pattern.compile(
          "\\{\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00\","
              + "\"path\":\"(?<path>[^\"]+)\",\"status\":(?<status>\\d{3}),"
              + "\"error\":\"(?<error>[^\"]+)\",\"requestId\":\"[0-9a-f]{1,8}\"}");

  @Autowired private ApplicationContext context;
  @Autowired private PreferencesRepository preferencesRepository;

  private WebTestClient webTestClient;

  @BeforeEach
  void setup() {
    webTestClient =
        WebTestClient.bindToApplicationContext(context)
            .apply(SecurityMockServerConfigurers.springSecurity())
            .configureClient()
            .build();
    preferencesRepository.truncateTable();
  }

  @Test
  void thatDefaultPreferencesRenderAnExplicitNull() {
    assertThat(get()).isEqualTo("{\"properties\":null}");
  }

  @Test
  void thatArbitraryJsonIsEchoedAndReadBackAsNormalisedByJsonb() {
    assertThat(write(HttpMethod.PUT, ARBITRARY_JSON))
        .isEqualTo(
            "{\"properties\":{\"zeta\":1,\"alpha\":{\"b\":[1,2.5,\"x\",true,null,{}],"
                + "\"a\":\"\u00e4\u2603\"},\"big\":12345678901234567890123,\"f\":0.1,"
                + "\"e\":1.0E300,\"d\":1.0,\"neg\":-0.0,\"long\":9007199254740993,\"nul\":null,"
                + "\"empty\":{},\"arr\":[],\"s\":\"2026-09-25T10:00:00Z\"}}");

    assertThat(get())
        .isEqualTo(
            "{\"properties\":{\"d\":1.0,\"e\":1"
                + "0".repeat(300)
                + ",\"f\":0.1,\"s\":\"2026-09-25T10:00:00Z\",\"arr\":[],"
                + "\"big\":12345678901234567890123,\"neg\":0.0,\"nul\":null,"
                + "\"long\":9007199254740993,\"zeta\":1,"
                + "\"alpha\":{\"a\":\"\u00e4\u2603\",\"b\":[1,2.5,\"x\",true,null,{}]},"
                + "\"empty\":{}}}");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"properties\":[1,{\"a\":2}]}",
        "{\"properties\":\"str\"}",
        "{\"properties\":42}",
      })
  void thatNonObjectPropertiesRoundTrip(String body) {
    assertThat(write(HttpMethod.POST, body)).isEqualTo(body);
    assertThat(get()).isEqualTo(body);
  }

  @Test
  void thatUnknownTopLevelFieldsAreIgnored() {
    assertThat(write(HttpMethod.POST, "{\"properties\":{\"a\":1},\"extra\":1}"))
        .isEqualTo("{\"properties\":{\"a\":1}}");
  }

  @Test
  void thatTheLastOfDuplicateKeysWins() {
    assertThat(write(HttpMethod.PUT, "{\"properties\":{\"k\":1,\"k\":2}}"))
        .isEqualTo("{\"properties\":{\"k\":2}}");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"properties\":{\"t\":1}} {\"x\":2}", "{\"properties\":{\"t\":1}}]"})
  void thatContentAfterTheFirstJsonValueIsIgnored(String body) {
    assertThat(write(HttpMethod.PUT, body)).isEqualTo("{\"properties\":{\"t\":1}}");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"properties\":null}",
        "[1]",
        "{\"properties\":{/*c*/\"a\":1}}",
        "{\"properties\":{\"a\":NaN}}",
        "{not json",
        ""
      })
  void thatInvalidBodiesAreRejectedWithAnEmptyBadRequest(String body) {
    authenticated()
        .put()
        .uri(PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .doesNotExist(HttpHeaders.CONTENT_TYPE)
        .expectBody()
        .isEmpty();
  }

  @Test
  void thatUnauthenticatedRequestsGetAnEmptyUnauthorized() {
    webTestClient
        .get()
        .uri(PATH)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectHeader()
        .doesNotExist(HttpHeaders.CONTENT_TYPE)
        .expectBody()
        .isEmpty();
  }

  @Test
  void thatFrameworkErrorsForUnknownPathsUseTheBootDefaultBody() {
    assertDefaultErrorBody(authenticated().get().uri("/v1/unknown").exchange(), "/v1/unknown");
  }

  @Test
  void thatFrameworkErrorsForUnsupportedMethodsUseTheBootDefaultBody() {
    assertDefaultErrorBody(authenticated().delete().uri(PATH).exchange(), PATH);
  }

  @Test
  void thatFrameworkErrorsForUnsupportedMediaTypesUseTheBootDefaultBody() {
    assertDefaultErrorBody(
        authenticated()
            .put()
            .uri(PATH)
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue("{\"properties\":{}}")
            .exchange(),
        PATH);
  }

  @Test
  void thatActuatorInfoRendersTimesAsIsoStrings() {
    String body =
        webTestClient
            .get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType("application/vnd.spring-boot.actuator.v3+json")
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(body)
        .containsPattern(
            "\"build\":\\{[^}]*\"time\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z\"")
        .containsPattern(
            "\"commit\":\\{[^}]*\"time\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z\"");
  }

  @Test
  void thatActuatorHealthBodyIsUnchanged() {
    webTestClient
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            "{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}", JsonCompareMode.STRICT);
  }

  private void assertDefaultErrorBody(WebTestClient.ResponseSpec response, String path) {
    var result =
        response
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult();

    var matcher = DEFAULT_ERROR_BODY.matcher(result.getResponseBody());
    assertThat(matcher.matches()).as(result.getResponseBody()).isTrue();
    assertThat(matcher.group("path")).isEqualTo(path);
    assertThat(Integer.parseInt(matcher.group("status")))
        .isEqualTo(result.getStatus().value())
        .isBetween(400, 599);
    assertThat(matcher.group("error"))
        .isEqualTo(HttpStatus.valueOf(result.getStatus().value()).getReasonPhrase());
  }

  private String get() {
    return authenticated()
        .get()
        .uri(PATH)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private String write(HttpMethod method, String body) {
    return authenticated()
        .method(method)
        .uri(PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private WebTestClient authenticated() {
    return webTestClient.mutateWith(
        SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")));
  }
}
