package com.processVisualisation.virtualKitchen.common.config;

import com.processVisualisation.virtualKitchen.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configures Spring Security for the application: password hashing, CORS,
 * CSRF, JWT-based request authentication, and which request paths require an
 * authenticated user.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Creates this configuration.
     *
     * @param jwtAuthenticationFilter filter that resolves the authenticated
     *                                user from the request's JWT before
     *                                Spring's default authentication filter runs
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Provides the password encoder used to hash and verify user passwords.
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Builds the application's {@link SecurityFilterChain}: enables CORS via
     * {@link #corsConfigurationSource()}, disables CSRF, requires
     * authentication on {@code /api/v1/auth/me} and on any request not
     * explicitly permitted, permits unauthenticated access to
     * {@code /swagger-ui/**}, {@code /v3/api-docs/**} and {@code /api/**},
     * inserts {@link #jwtAuthenticationFilter} before
     * {@link UsernamePasswordAuthenticationFilter} so the authenticated user
     * is resolved from the JWT first, and enables HTTP Basic authentication.
     *
     * @param http the {@link HttpSecurity} builder to configure
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if the security chain fails to build
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Enable CORS for the Vite frontend
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Disable CSRF so you can use POST/PUT without tokens
                .csrf(csrf -> csrf.disable())

                // 3. Authorize requests
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/me").authenticated() // requires a valid JWT
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api/**").permitAll() // Open API endpoints for local development
                        .anyRequest().authenticated() // Everything else needs login
                )

                // 4. Resolve the authenticated user (if any) from the JWT before Spring's default auth filter runs
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 5. Enable Basic Auth (This makes the popup login work)
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Defines the CORS policy applied to every endpoint ({@code /**}):
     * allows the local Vite dev frontend origins ({@code http://localhost:5173}
     * and {@code http://127.0.0.1:5173}), the GET/POST/PUT/PATCH/DELETE/OPTIONS
     * methods, any request header, and credentialed (cookie/auth-header) requests.
     *
     * @return the {@link CorsConfigurationSource} used by {@link #securityFilterChain}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

