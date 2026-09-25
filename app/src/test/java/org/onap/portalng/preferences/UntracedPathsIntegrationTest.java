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

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UntracedPathsIntegrationTest {

  @Autowired private WebTestClient webTestClient;

  @TestConfiguration
  static class JwtDecoderConfig {
    @Bean
    ReactiveJwtDecoder jwtDecoder() {
      return token ->
          Mono.just(
              Jwt.withTokenValue(token)
                  .header("alg", "none")
                  .subject("user")
                  .issuedAt(Instant.now())
                  .expiresAt(Instant.now().plusSeconds(60))
                  .build());
    }
  }

  @Test
  void thatProbesAndScrapesAreNotObservedWhileApiRequestsAre() {
    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
    webTestClient
        .get()
        .uri("/v1/preferences")
        .headers(headers -> headers.setBearerAuth("token"))
        .exchange()
        .expectStatus()
        .isOk();
    scrape();

    String metrics = scrape();

    assertThat(metrics)
        .doesNotContain("uri=\"/actuator/health")
        .doesNotContain("uri=\"/actuator/prometheus\"")
        .contains("uri=\"/v1/preferences\"");
  }

  private String scrape() {
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
}
