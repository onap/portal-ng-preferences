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

package org.onap.portalng.preferences.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The default preferences exception */
public class ProblemException extends ErrorResponseException {

  public ProblemException(HttpStatus status, String detail) {
    super(status, problemDetail(status, detail), null);
  }

  private static ProblemDetail problemDetail(HttpStatus status, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(status);
    problemDetail.setTitle("Bad preferences error");
    problemDetail.setDetail(detail);
    return problemDetail;
  }
}
