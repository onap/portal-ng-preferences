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

package org.onap.portalng.preferences.util;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

@Slf4j
public class Logger {

  private Logger() {}

  public static void requestLog(HttpMethod methode, URI path) {
    log.info("Preferences - request - {} {}", methode, path);
  }

  public static void responseLog(HttpStatusCode httpStatusCode) {
    log.info("Preferences - response - {}", httpStatusCode);
  }

  public static void errorLog(String msg, String id, String app) {
    log.info("Preferences - error - {} {} not found in {}", msg, id, app);
  }

  public static void errorLog(String msg, String id, String app, String errorDetails) {
    log.info(
        "Preferences - error - {} {} not found in {} error message: {}",
        msg,
        id,
        app,
        errorDetails);
  }
}
