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

package org.onap.portalng.preferences.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.onap.portalng.preferences.exception.ProblemException;
import org.onap.portalng.preferences.openapi.api.PreferencesApi;
import org.onap.portalng.preferences.openapi.model.PreferencesApiDto;
import org.onap.portalng.preferences.services.PreferencesService;
import org.onap.portalng.preferences.util.IdTokenExchange;
import org.onap.portalng.preferences.util.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class PreferencesController implements PreferencesApi {

  private static final String UPDATES_METRIC = "preferences.updates";

  private final PreferencesService preferencesService;
  private final MeterRegistry meterRegistry;

  public PreferencesController(PreferencesService getPreferences, MeterRegistry meterRegistry) {
    this.preferencesService = getPreferences;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<ResponseEntity<PreferencesApiDto>> getPreferences(ServerWebExchange exchange) {
    return IdTokenExchange.extractUserId(exchange)
        .flatMap(userid -> preferencesService.getPreferences(userid).map(ResponseEntity::ok))
        .onErrorResume(
            ProblemException.class,
            ex -> {
              Logger.errorLog("user preferences", null, "preferences");
              return Mono.error(ex);
            })
        .onErrorReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
  }

  @Override
  public Mono<ResponseEntity<PreferencesApiDto>> savePreferences(
      Mono<PreferencesApiDto> preferences, ServerWebExchange exchange) {
    return writePreferences(preferences, exchange, "save");
  }

  @Override
  public Mono<ResponseEntity<PreferencesApiDto>> updatePreferences(
      Mono<PreferencesApiDto> preferences, ServerWebExchange exchange) {
    return writePreferences(preferences, exchange, "update");
  }

  private Mono<ResponseEntity<PreferencesApiDto>> writePreferences(
      Mono<PreferencesApiDto> preferences, ServerWebExchange exchange, String operation) {
    return IdTokenExchange.extractUserId(exchange)
        .flatMap(
            userid -> preferences.flatMap(pref -> preferencesService.savePreferences(userid, pref)))
        .map(ResponseEntity::ok)
        .doOnNext(response -> countUpdate(operation, "success"))
        .doOnError(ex -> countUpdate(operation, "failure"))
        .onErrorResume(
            ProblemException.class,
            ex -> {
              Logger.errorLog("user preferences", null, "preferences");
              return Mono.error(ex);
            })
        .onErrorReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
  }

  private void countUpdate(String operation, String outcome) {
    Counter.builder(UPDATES_METRIC)
        .description("Preference writes by operation and outcome")
        .tag("operation", operation)
        .tag("outcome", outcome)
        .register(meterRegistry)
        .increment();
  }
}
