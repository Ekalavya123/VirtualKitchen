package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verifies Google Sign-In ID tokens against Google's tokeninfo endpoint.
 */
@Service
public class GoogleTokenVerifierService {

    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.oauth.client-id}")
    private String clientId;

    /**
     * Verifies the given Google ID token by calling Google's tokeninfo
     * endpoint, confirming it was issued for this application (audience
     * claim matches the configured client id) and that the associated Google
     * account email is verified.
     *
     * @param idToken the Google-issued ID token to verify
     * @return the verified identity (Google id, email, name) extracted from the token
     * @throws AuthException if the token cannot be reached/parsed, is invalid or expired, was not issued for this application, or belongs to an unverified email
     */
    public GoogleUserInfo verify(String idToken) {
        Map<String, Object> body;

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response =
                    (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.getForEntity(TOKEN_INFO_URL + idToken, Map.class);
            body = response.getBody();
        } catch (RestClientException e) {
            throw new AuthException("Invalid or expired Google token", HttpStatus.UNAUTHORIZED);
        }

        if (body == null) {
            throw new AuthException("Unable to verify Google token", HttpStatus.UNAUTHORIZED);
        }

        if (clientId == null || clientId.isBlank() || !clientId.equals(body.get("aud"))) {
            throw new AuthException("Google token was not issued for this application", HttpStatus.UNAUTHORIZED);
        }

        boolean emailVerified = Boolean.parseBoolean(String.valueOf(body.get("email_verified")));

        if (!emailVerified) {
            throw new AuthException("Google account email is not verified", HttpStatus.UNAUTHORIZED);
        }

        return new GoogleUserInfo(
                String.valueOf(body.get("sub")),
                String.valueOf(body.get("email")),
                String.valueOf(body.getOrDefault("name", body.get("email")))
        );
    }
}
