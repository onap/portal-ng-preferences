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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.portalng.preferences.openapi.model.PreferencesApiDto;
import org.onap.portalng.preferences.repository.PreferencesRepository;
import org.onap.portalng.preferences.services.PreferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    preferencesService.savePreferences("user", prefs);
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
