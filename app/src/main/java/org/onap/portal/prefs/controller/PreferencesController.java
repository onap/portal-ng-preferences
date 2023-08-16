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
import org.onap.portalng.preferences.exception.ProblemException;
import org.onap.portalng.preferences.openapi.api.PreferencesApi;
import org.onap.portalng.preferences.openapi.model.Preferences;
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


  private final PreferencesService preferencesService;

  public PreferencesController(PreferencesService getPreferences){
    this.preferencesService = getPreferences;
  }

  @Override
  public Mono<ResponseEntity<Preferences>> getPreferences(String xRequestId, ServerWebExchange exchange) {
    return IdTokenExchange
      .extractUserId(exchange)
      .flatMap(userid ->
        preferencesService.getPreferences(userid)
          .map(ResponseEntity::ok))
          .onErrorResume(ProblemException.class, ex -> {
              Logger.errorLog(xRequestId,"user preferences", null, "preferences" );
              return Mono.error(ex);
          })
      .onErrorReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));

  }

  @Override
  public Mono<ResponseEntity<Preferences>> savePreferences(String xRequestId, Mono<Preferences> preferences,
                                                           ServerWebExchange exchange) {
  return IdTokenExchange
    .extractUserId(exchange)
    .flatMap(userid ->
      preferences
        .flatMap( pref ->
          preferencesService
            .savePreferences(xRequestId, userid, pref)))
            .map( ResponseEntity::ok)
        .onErrorResume(ProblemException.class, ex -> {
          Logger.errorLog(xRequestId,"user preferences", null, "preferences" );
          return Mono.error(ex);
        })
    .onErrorReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
  }

  @Override
  public Mono<ResponseEntity<Preferences>> updatePreferences(String xRequestId, Mono<Preferences> preferences, ServerWebExchange exchange) {
    return savePreferences(xRequestId, preferences, exchange);
  }

}
