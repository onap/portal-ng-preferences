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

package org.onap.portal.prefs.services;

import org.onap.portal.prefs.entities.PreferencesDto;
import org.onap.portal.prefs.exception.ProblemException;
import org.onap.portal.prefs.openapi.model.Preferences;
import org.onap.portal.prefs.repository.PreferencesRepository;
import org.onap.portal.prefs.util.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class PreferencesService {

  @Autowired
  private PreferencesRepository repository;

  public Mono<Preferences> getPreferences(String userId){
    return repository
      .findById(userId)
      .switchIfEmpty(defaultPreferences())
      .map(this::toPreferences);
  }

  public Mono<Preferences> savePreferences( String xRequestId, String userId, Preferences preferences){

    var preferencesDto = new PreferencesDto();
    preferencesDto.setUserId(userId);
    preferencesDto.setProperties(preferences.getProperties());

    return repository
      .save(preferencesDto)
      .map(this::toPreferences)  
      .onErrorResume(ProblemException.class, ex -> {
        Logger.errorLog(xRequestId,"user prefrences", userId, "portal-prefs" );
        return Mono.error(ex);
      });

  }

  private Preferences toPreferences(PreferencesDto preferencesDto) {
    var preferences = new Preferences();
    preferences.setProperties(preferencesDto.getProperties());
    return preferences; 
  }

  /**
   * Get a Preferences object that is initialised with an empty string.
   * This is a) for convenience to not handle 404 on the consuming side and
   * b) for security reasons
   * @return PreferencesDto
   */
  private Mono<PreferencesDto> defaultPreferences() {
    var preferencesDto = new PreferencesDto();
    preferencesDto.setProperties("");
    return Mono.just(preferencesDto);
  }
}
