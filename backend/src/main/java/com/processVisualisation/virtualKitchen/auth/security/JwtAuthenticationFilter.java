package com.processVisualisation.virtualKitchen.auth.security;

import com.processVisualisation.virtualKitchen.auth.model.UserType;
import com.processVisualisation.virtualKitchen.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security filter, installed once per request in the security filter
 * chain, that populates the security context from a Bearer JWT when present.
 * Invalid/missing tokens are left unauthenticated rather than rejected here,
 * so existing permitAll endpoints keep working; endpoints that require
 * authentication rely on downstream authorization checks (e.g. an
 * authenticated Authentication object being required) to reject requests
 * with no valid principal.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Inspects the incoming request for an Authorization: Bearer token. When
     * present and valid, extracts the user id and user type via JwtService and
     * populates the SecurityContextHolder with an authenticated principal
     * (using the user id as principal and a ROLE_&lt;UserType&gt; authority).
     * Any failure while parsing or validating the token (expired signature,
     * malformed token, unknown claims, etc.) is swallowed and results in the
     * security context being cleared, so the request proceeds unauthenticated
     * rather than being rejected by this filter. Requests without a Bearer
     * header pass through untouched. In all cases the filter chain is invoked
     * exactly once to continue processing the request.
     *
     * @param request the incoming HTTP request, inspected for an Authorization header
     * @param response the outgoing HTTP response, passed through unmodified to the filter chain
     * @param filterChain the remaining filter chain to continue processing after this filter runs
     * @throws ServletException if a downstream filter or servlet in the chain throws it
     * @throws IOException if a downstream filter or servlet in the chain throws it, or an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring("Bearer ".length());
                Long userId = jwtService.extractUserId(token);
                UserType userType = jwtService.extractUserType(token);

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + userType.name()));
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
