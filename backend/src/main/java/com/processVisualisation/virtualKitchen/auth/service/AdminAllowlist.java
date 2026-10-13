package com.processVisualisation.virtualKitchen.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The emails configured in {@code vk.admin.emails} (comma-separated, empty by default). A verified user with one of
 * these emails is promoted to {@code ADMIN} the next time a token is issued for them; nobody is ever demoted
 * automatically. This only assigns the existing {@code UserType.ADMIN} role — enforcement stays with
 * {@code SecurityConfig} and {@code CurrentUser.requireAdmin()}.
 */
@Component
public class AdminAllowlist {

    private final Set<String> emails;

    public AdminAllowlist(@Value("${vk.admin.emails:}") String emails) {
        this.emails = Arrays.stream(emails == null ? new String[0] : emails.split(","))
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean contains(String email) {
        return email != null && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
