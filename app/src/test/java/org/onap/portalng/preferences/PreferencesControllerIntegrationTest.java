/*
 *
 * Copyright (c) 2025. Deutsche Telekom AG
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.portalng.preferences.openapi.model.PreferencesApiDto;
import org.onap.portalng.preferences.repository.PreferencesRepository;
import org.onap.portalng.preferences.services.PreferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class PreferencesControllerIntegrationTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setup(
      final ApplicationContext context,
      @Autowired final PreferencesRepository preferencesRepository) {
    webTestClient =
        WebTestClient.bindToApplicationContext(context)
            .apply(SecurityMockServerConfigurers.springSecurity())
            .configureClient()
            .build();
    preferencesRepository.truncateTable();
  }

  @Test
  void testAuthenticatedAccess() {
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get()
        .uri("/v1/preferences")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void testUnauthorizedAccess() {
    webTestClient.get().uri("/v1/preferences").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void thatDefaultUserPreferencesCanBeRetrieved() throws Exception {
    final var prefs = getDefaultPreferencesApiDto();
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get()
        .uri("/v1/preferences")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(objectMapper.writeValueAsString(prefs));
  }

  @Test
  void thatSimpleUserPreferencesCanBeSaved() throws Exception {
    final var prefs = getSimplePreferencesApiDto();
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .post()
        .uri("/v1/preferences")
        .bodyValue(prefs)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(objectMapper.writeValueAsString(prefs));
  }

  @Test
  void thatSimpleUserPreferencesCanBeUpdated() throws Exception {
    final var prefs = getSimplePreferencesApiDto();
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .put()
        .uri("/v1/preferences")
        .bodyValue(prefs)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(objectMapper.writeValueAsString(prefs));
  }

  @Test
  void thatComplexUserPreferencesCanBeRetrieved(
      @Autowired final PreferencesService preferencesService) throws Exception {
    final var prefs = getComplexPreferencesApiDto();
    // block() to subscribe: savePreferences is now lazy (deferred via Mono.fromCallable) instead
    // of eagerly saving during Mono assembly, so the save only runs once subscribed.
    preferencesService.savePreferences("user", prefs).block();
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get()
        .uri("/v1/preferences")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(objectMapper.writeValueAsString(prefs));
  }

  @Test
  void thatPreferenceUpdatesAreCountedByOperationAndOutcome() throws Exception {
    final var prefs = getSimplePreferencesApiDto();
    final var before = scrapeMetrics();

    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .post()
        .uri("/v1/preferences")
        .bodyValue(prefs)
        .exchange()
        .expectStatus()
        .isOk();
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .put()
        .uri("/v1/preferences")
        .bodyValue(prefs)
        .exchange()
        .expectStatus()
        .isOk();
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .put()
        .uri("/v1/preferences")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{not json")
        .exchange()
        .expectStatus()
        .isBadRequest();

    final var after = scrapeMetrics();
    assertThat(updates(after, "save", "success") - updates(before, "save", "success")).isEqualTo(1);
    assertThat(updates(after, "update", "success") - updates(before, "update", "success"))
        .isEqualTo(1);
    assertThat(updates(after, "update", "failure") - updates(before, "update", "failure"))
        .isEqualTo(1);
  }

  private String scrapeMetrics() {
    return webTestClient
        .get()
        .uri("/actuator/prometheus")
        .accept(MediaType.TEXT_PLAIN)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private static double updates(String metrics, String operation, String outcome) {
    Matcher matcher =
        Pattern.compile(
                "^preferences_updates_total\\{operation=\"%s\",outcome=\"%s\"} (\\S+)$"
                    .formatted(operation, outcome),
                Pattern.MULTILINE)
            .matcher(metrics);
    return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
  }

  private PreferencesApiDto getDefaultPreferencesApiDto() {
    return new PreferencesApiDto().properties(null);
  }

  private PreferencesApiDto getSimplePreferencesApiDto() throws Exception {
    return new PreferencesApiDto()
        .properties(objectMapper.readValue("{\"appStarter\":\"appStarterValue\"}", Map.class));
  }

  private PreferencesApiDto getComplexPreferencesApiDto() throws Exception {
    return new PreferencesApiDto()
        .properties(
            objectMapper.readValue(
                "{\"appStarter\":\"appStarterValue1\", \"dashboard\":{\"dashboardKey\":\"dashboardValue\"}}",
                Map.class));
  }
}
