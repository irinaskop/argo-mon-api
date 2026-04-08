package org.grnet.status.authorizations.tokens;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.grnet.status.authorizations.clients.KeycloakTokenClient;

@ApplicationScoped
//@IfBuildProfile("prod")
public class KeycloakClientCredentialsTokenProvider implements AccessTokenProvider{

    @Inject
    @RestClient
    KeycloakTokenClient tokenClient;

    @ConfigProperty(name = "auth.group.management.client-id")
    String clientId;

    @ConfigProperty(name = "auth.group.management.client-secret")
    String clientSecret;

    public String getAccessToken() {
        return tokenClient
                .getToken("client_credentials", clientId, clientSecret)
                .access_token;
    }
}
