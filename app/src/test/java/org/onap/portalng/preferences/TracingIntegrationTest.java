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

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.tracing.export.enabled=true",
      "management.tracing.export.zipkin.enabled=false"
    })
@AutoConfigureWebTestClient
@ExtendWith(OutputCaptureExtension.class)
class TracingIntegrationTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
  private static final String REQUEST_ID = "request-id-from-bff";

  @Autowired private WebTestClient webTestClient;
  @Autowired private InMemorySpanExporter spanExporter;
  @Autowired private SdkTracerProvider tracerProvider;
  @Autowired private JsonMapper jsonMapper;

  @TestConfiguration
  static class TestConfig {
    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
      return InMemorySpanExporter.create();
    }

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

  @BeforeEach
  void resetSpans() {
    tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
    spanExporter.reset();
  }

  @Test
  void thatRequestLogLinesCarryTraceIdAndRequestId(CapturedOutput output) {
    getPreferences();

    List<JsonNode> requestLines =
        logLines(output).stream()
            .filter(line -> REQUEST_ID.equals(line.path("request_id").asString()))
            .toList();

    assertThat(requestLines)
        .extracting(line -> line.path("message").asString())
        .contains("RECEIVED", "FINISHED");
    assertThat(requestLines)
        .allSatisfy(
            line -> {
              assertThat(line.path("trace_id").asString()).isEqualTo(TRACE_ID);
              assertThat(line.path("span_id").asString()).matches("[0-9a-f]{16}");
            });
  }

  @Test
  void thatDatabaseCallsAreTraced() {
    getPreferences();

    tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
    List<SpanData> spans =
        spanExporter.getFinishedSpanItems().stream()
            .filter(span -> TRACE_ID.equals(span.getTraceId()))
            .toList();

    assertThat(spans).extracting(SpanData::getName).contains("connection", "query");
  }

  private void getPreferences() {
    webTestClient
        .get()
        .uri("/v1/preferences")
        .headers(
            headers -> {
              headers.setBearerAuth("token");
              headers.set("traceparent", TRACEPARENT);
              headers.set("X-Request-Id", REQUEST_ID);
            })
        .exchange()
        .expectStatus()
        .isOk();
  }

  private List<JsonNode> logLines(CapturedOutput output) {
    return output
        .getOut()
        .lines()
        .filter(line -> line.startsWith("{"))
        .map(
            line -> {
              try {
                return jsonMapper.readTree(line);
              } catch (Exception e) {
                return jsonMapper.nullNode();
              }
            })
        .toList();
  }
}
