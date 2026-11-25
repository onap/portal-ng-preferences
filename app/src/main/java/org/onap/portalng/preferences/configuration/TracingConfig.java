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

import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.autoconfigure.ConditionalOnEnabledTracingExport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.util.AntPathMatcher;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.SpanBytesEncoder;

@Configuration
public class TracingConfig {

  private static final List<String> UNTRACED_PATHS =
      List.of("/actuator/health/**", "/actuator/prometheus");

  // Spring Boot 4.2 still configures the Zipkin sender but leaves exporter wiring to the app.
  @Bean
  @ConditionalOnEnabledTracingExport("zipkin")
  @SuppressWarnings("deprecation")
  ZipkinSpanExporter zipkinSpanExporter(BytesMessageSender sender, Encoding encoding) {
    return ZipkinSpanExporter.builder()
        .setSender(sender)
        .setEncoder(SpanBytesEncoder.forEncoding(encoding))
        .build();
  }

  @Bean
  ObservationPredicate untracedPathsPredicate(
      @Value("${preferences.tracing.untraced-paths-enabled:true}") boolean enabled) {
    AntPathMatcher pathMatcher = new AntPathMatcher();
    return (name, context) -> {
      if (!enabled || !(context instanceof ServerRequestObservationContext serverContext)) {
        return true;
      }
      String path = serverContext.getCarrier().getPath().pathWithinApplication().value();
      return UNTRACED_PATHS.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    };
  }
}
