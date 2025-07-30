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

package org.onap.portalng.preferences.services;

import org.onap.portalng.preferences.entities.PreferencesDto;
import org.onap.portalng.preferences.exception.ProblemException;
import org.onap.portalng.preferences.openapi.model.PreferencesApiDto;
import org.onap.portalng.preferences.repository.PreferencesRepository;
import org.onap.portalng.preferences.util.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public class PreferencesService {

  private final PreferencesRepository repository;

  private final ObjectMapper objectMapper;

  public Mono<PreferencesApiDto> getPreferences(String userId){
    return Mono.just(repository
      .findById(userId)
      .orElse(defaultPreferences()))
      .map(this::toPreferences);
  }

  public Mono<PreferencesApiDto> savePreferences(String userId, PreferencesApiDto preferences){

    var preferencesDto = new PreferencesDto();
    preferencesDto.setUserId(userId);
    preferencesDto.setProperties(objectMapper.valueToTree(preferences.getProperties()));

    return Mono.just(repository.save(preferencesDto))
      .map(this::toPreferences)
      .onErrorResume(ProblemException.class, ex -> {
        Logger.errorLog("user prefrences", userId, "preferences" );
        return Mono.error(ex);
      });

  }

  private PreferencesApiDto toPreferences(PreferencesDto preferencesDto) {
    var preferences = new PreferencesApiDto();
    preferences.setProperties(preferencesDto.getProperties());
    return preferences;
  }

  /**
   * Get a Preferences object that is initialised with an empty string.
   * This is a) for convenience to not handle 404 on the consuming side and
   * b) for security reasons
   * @return PreferencesDto
   */
  private PreferencesDto defaultPreferences() {
    var preferencesDto = new PreferencesDto();
    preferencesDto.setProperties(null);
    return preferencesDto;
  }
}
