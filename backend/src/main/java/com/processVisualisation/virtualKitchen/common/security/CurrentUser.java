package com.processVisualisation.virtualKitchen.common.security;

import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the caller's identity from the security context populated by {@code JwtAuthenticationFilter}
 * (principal = the {@code Long} user id, authority = {@code ROLE_<userType>}). Identity and role
 * always come from the verified token, never from request bodies or parameters.
 */
public final class CurrentUser {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private CurrentUser() {
    }

    /**
     * @throws AuthException (401) when the request is not authenticated
     */
    public static Long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        if (!(principal instanceof Long userId)) {
            throw new AuthException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * Requires an authenticated ADMIN and returns their user id. A defense-in-depth check: the
     * {@code /api/v1/admin/**} URL rule in {@code SecurityConfig} already enforces the role.
     *
     * @throws AuthException 401 when unauthenticated, 403 when authenticated without the ADMIN role
     */
    public static Long requireAdmin() {
        Long userId = requireUserId();
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
        if (!isAdmin) {
            throw new AuthException("Admin privileges required", HttpStatus.FORBIDDEN);
        }
        return userId;
    }
}
