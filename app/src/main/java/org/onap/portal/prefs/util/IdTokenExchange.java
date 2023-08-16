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

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

import java.text.ParseException;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Represents a function that handles the <a href="https://jwt.io/introduction">JWT</a> identity token.
 * Use this to check if the incoming requests are authorized to call the given endpoint
 */

public final class IdTokenExchange {

  public static final String X_AUTH_IDENTITY_HEADER = "X-Auth-Identity";
  public static final String JWT_CLAIM_USERID = "sub";

  private IdTokenExchange(){

  }

  /**
   * Extract the identity header from the given {@link ServerWebExchange}.
   * @param exchange the ServerWebExchange that contains information about the incoming request
   * @return the identity header in the form of <code>Bearer {@literal <Token>}<c/ode>
   */
  private static Mono<String> extractIdentityHeader(ServerWebExchange exchange) {
    return Mono.just(exchange.getRequest().getHeaders().getOrEmpty(X_AUTH_IDENTITY_HEADER))
        .map(headers -> headers.get(0))
        .onErrorResume(Exception.class, ex -> Mono.error(ex));
  }

    /**
   * Extract the identity token from the given {@link ServerWebExchange}.
   * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenId Connect ID Token</a>
   * @param exchange the ServerWebExchange that contains information about the incoming request
   * @return the identity token that contains user roles
   */
  private static Mono<String> extractIdToken(ServerWebExchange exchange) {
    return extractIdentityHeader(exchange)
        .map(identityHeader -> identityHeader.replace("Bearer ", ""));
  }

  /**
   * Extract the <code>userId</code> from the given {@link ServerWebExchange}
   * @param exchange the ServerWebExchange that contains information about the incoming request
   * @return the id of the user
   */
  public static Mono<String> extractUserId(ServerWebExchange exchange) {
    return extractIdToken(exchange)
        .flatMap(idToken -> extractUserClaim(idToken));
  }

  private static Mono<String> extractUserClaim(String idToken) {
    JWTClaimsSet jwtClaimSet;
	try {
		jwtClaimSet = JWTParser.parse(idToken).getJWTClaimsSet();
	} catch (ParseException e) {
		return Mono.error(e);
	}
    return Mono.just(String.class.cast(jwtClaimSet.getClaim(JWT_CLAIM_USERID)));
  }
}

