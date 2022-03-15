package no.rutebanken.anshar.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TokenService {
    private static Keycloak keycloakClient;
    private static Logger log = LoggerFactory.getLogger(TokenService.class);

    private TokenService() {
    }

    public static String getToken() {

        if (keycloakClient == null ){
            String clientId = getAndValidateProperty("iam.keycloak.admin.client");
            String clientSecret = getAndValidateProperty("iam.keycloak.client.secret");
            String realm = getAndValidateProperty("keycloak.realm");
            String authServerUrl = getAndValidateProperty("keycloak.auth-server-url");

            keycloakClient = KeycloakBuilder.builder().clientId(clientId).clientSecret(clientSecret).realm(realm).serverUrl(authServerUrl).grantType("client_credentials").build();
        }

        return keycloakClient.tokenManager().getAccessTokenString();
    }

    private static String getAndValidateProperty(String propertyKey) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue == null) {
            log.warn("Cannot read property " + propertyKey );
        }
        return propertyValue;
    }
}
