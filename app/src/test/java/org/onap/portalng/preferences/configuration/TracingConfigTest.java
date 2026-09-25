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

package org.onap.portalng.preferences.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class TracingConfigTest {

  private final ObservationPredicate predicate = new TracingConfig().untracedPathsPredicate(true);

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness",
        "/actuator/prometheus"
      })
  void thatProbeAndScrapeRequestsAreNotObserved(String path) {
    assertFalse(predicate.test("http.server.requests", serverContext(path)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v1/preferences", "/actuator", "/actuator/info"})
  void thatOtherRequestsAreObserved(String path) {
    assertTrue(predicate.test("http.server.requests", serverContext(path)));
  }

  @Test
  void thatPathsAreMatchedWithinTheBasePath() {
    ServerHttpRequest request =
        MockServerHttpRequest.get("/preferences/actuator/health/liveness")
            .build()
            .mutate()
            .contextPath("/preferences")
            .build();

    assertFalse(predicate.test("http.server.requests", serverContext(request)));
  }

  @Test
  void thatProbeRequestsAreObservedWhenDisabled() {
    ObservationPredicate disabled = new TracingConfig().untracedPathsPredicate(false);

    assertTrue(disabled.test("http.server.requests", serverContext("/actuator/health/liveness")));
  }

  @Test
  void thatNonServerRequestObservationsAreObserved() {
    assertTrue(predicate.test("http.client.requests", new Observation.Context()));
  }

  private static ServerRequestObservationContext serverContext(String path) {
    return serverContext(MockServerHttpRequest.get(path).build());
  }

  private static ServerRequestObservationContext serverContext(ServerHttpRequest request) {
    return new ServerRequestObservationContext(request, new MockServerHttpResponse(), Map.of());
  }
}
