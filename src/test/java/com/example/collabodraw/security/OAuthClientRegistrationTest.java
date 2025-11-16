package com.example.collabodraw.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OAuthClientRegistrationTest {

    @Autowired(required = false)
    ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void googleClientRegistrationPresent() {
        assertThat(clientRegistrationRepository)
            .as("ClientRegistrationRepository should be wired when OAuth properties are set")
            .isNotNull();
        ClientRegistration google = null;
        if (clientRegistrationRepository != null) {
            try {
                google = clientRegistrationRepository.findByRegistrationId("google");
            } catch (Exception ignored) {}
        }
        assertThat(google).as("Google registration should be available").isNotNull();
        assertThat(google.getClientId()).isEqualTo("test-client-id");
    }
}
