package org.onap.portalng.preferences;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PreferencesControllerIntegrationTest {

  @Autowired
  private WebTestClient webTestClient;

  @BeforeEach
  void setup(ApplicationContext context) {
    webTestClient = WebTestClient
        .bindToApplicationContext(context) // bind directly to Spring WebFlux stack
        .apply(SecurityMockServerConfigurers.springSecurity()) // enable security
        .configureClient()
        .build();
  }

  @Test
  void testAuthenticatedAccess() {
    webTestClient.mutateWith(SecurityMockServerConfigurers.mockJwt().jwt(jwt -> jwt.claim("sub", "user")))
        .get().uri("/v1/preferences")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  void testUnauthorizedAccess() {
    webTestClient
        .get().uri("/v1/preferences")
        .exchange()
        .expectStatus().isUnauthorized();
  }
}
