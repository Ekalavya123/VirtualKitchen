package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.dto.LoginRequestDTO;
import com.processVisualisation.virtualKitchen.auth.mapper.UserMapper;
import com.processVisualisation.virtualKitchen.auth.model.User;
import com.processVisualisation.virtualKitchen.auth.model.UserType;
import com.processVisualisation.virtualKitchen.auth.repository.UserRepository;
import com.processVisualisation.virtualKitchen.auth.security.JwtService;
import com.processVisualisation.virtualKitchen.common.persistence.SequenceGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code vk.admin.emails} assigns the existing ADMIN role at sign-in; it never demotes and never skips verification. */
class AdminPromotionTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);

    private AuthService service(String allowlist) {
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwt.generateToken(any(), any(), any())).thenReturn("token");
        return new AuthService(users, mock(SequenceGeneratorService.class), new UserMapper(), encoder, jwt,
                mock(OtpService.class), mock(GoogleTokenVerifierService.class), mock(ApplicationEventPublisher.class),
                new AdminAllowlist(allowlist));
    }

    private User user(String email, boolean verified, UserType type) {
        User user = new User();
        user.setId(5L);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setEmailVerified(verified);
        user.setUserType(type);
        when(users.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private static LoginRequestDTO login(String email) {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail(email);
        request.setPassword("pw");
        return request;
    }

    @Test
    void allowlistedVerifiedUser_isPromotedAndTheTokenCarriesTheRole() {
        user("chef@example.test", true, UserType.USER);

        service(" Chef@Example.test , other@example.test").login(login("chef@example.test"));

        verify(users).save(any(User.class));
        verify(jwt).generateToken(eq(5L), eq("chef@example.test"), eq(UserType.ADMIN));
    }

    @Test
    void otherUsers_stayUsers() {
        user("cook@example.test", true, UserType.USER);

        service("chef@example.test").login(login("cook@example.test"));

        verify(users, never()).save(any(User.class));
        verify(jwt).generateToken(eq(5L), eq("cook@example.test"), eq(UserType.USER));
    }

    @Test
    void emptyAllowlist_promotesNobody() {
        assertThat(new AdminAllowlist("").contains("chef@example.test")).isFalse();
        assertThat(new AdminAllowlist(null).contains("chef@example.test")).isFalse();
        assertThat(new AdminAllowlist("a@example.test").contains(null)).isFalse();
    }
}
