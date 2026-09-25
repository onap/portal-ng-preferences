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

package org.onap.portalng.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorIntegrationTest {
  @Autowired private ApplicationAvailability applicationAvailability;

  private WebTestClient webTestClient;

  @BeforeEach
  void setup(final ApplicationContext context) {
    webTestClient =
        WebTestClient.bindToApplicationContext(context)
            .apply(SecurityMockServerConfigurers.springSecurity())
            .configureClient()
            .build();
  }

  @Test
  void livenessProbeIsAvailable() {
    assertEquals(applicationAvailability.getLivenessState(), LivenessState.CORRECT);
  }

  @Test
  void readinessProbeIsAvailable() {
    assertEquals(applicationAvailability.getReadinessState(), ReadinessState.ACCEPTING_TRAFFIC);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness",
        "/actuator/info"
      })
  void thatProbeAndInfoEndpointsAreReachableWithoutToken(String path) {
    webTestClient.get().uri(path).exchange().expectStatus().isOk();
  }

  @Test
  void thatPrometheusEndpointIsReachableWithoutToken() {
    webTestClient
        .get()
        .uri("/actuator/prometheus")
        .accept(MediaType.TEXT_PLAIN)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator",
        "/actuator/env",
        "/actuator/configprops",
        "/actuator/beans",
        "/actuator/loggers",
        "/actuator/threaddump",
        "/actuator/heapdump",
        "/actuator/mappings"
      })
  void thatOtherActuatorEndpointsRequireToken(String path) {
    webTestClient.get().uri(path).exchange().expectStatus().isUnauthorized();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator/env",
        "/actuator/configprops",
        "/actuator/beans",
        "/actuator/loggers",
        "/actuator/threaddump",
        "/actuator/heapdump",
        "/actuator/mappings"
      })
  void thatOtherActuatorEndpointsAreNotExposed(String path) {
    webTestClient
        .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get()
        .uri(path)
        .exchange()
        .expectStatus()
        .isNotFound();
  }
}
