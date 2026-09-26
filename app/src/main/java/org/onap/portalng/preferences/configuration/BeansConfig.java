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

package org.onap.portalng.preferences.configuration;

import java.time.Clock;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.util.StdDateFormat;

@Configuration
public class BeansConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Jackson 3 writes a UTC {@link java.util.Date} as {@code ...Z}, Jackson 2 wrote {@code
   * ...+00:00}. Keeps the {@code timestamp} of Boot's error body in the Jackson 2 form.
   */
  @Bean
  JsonMapperBuilderCustomizer zeroOffsetDateFormatCustomizer() {
    return builder -> builder.defaultDateFormat(new StdDateFormat().withZeroOffsetAsZ(false));
  }
}
