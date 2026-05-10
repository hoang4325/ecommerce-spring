package com.example.ecommerce.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ecommerce.authservice.dto.AuthResponse;
import com.example.ecommerce.authservice.dto.LoginRequest;
import com.example.ecommerce.authservice.dto.RegisterRequest;
import com.example.ecommerce.authservice.entity.AuthUser;
import com.example.ecommerce.authservice.entity.Role;
import com.example.ecommerce.authservice.exception.DuplicateEmailException;
import com.example.ecommerce.authservice.exception.InvalidCredentialsException;
import com.example.ecommerce.authservice.repository.AuthUserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerHashesPasswordAssignsUserSavesNormalizedEmailAndReturnsToken() {
        RegisterRequest request = new RegisterRequest(" Customer@Example.com ", "password123", "Customer Name");
        when(authUserRepository.existsByEmailIgnoreCase("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(jwtTokenService.issueToken(any(AuthUser.class))).thenReturn("jwt-token");
        when(jwtTokenService.expiresInSeconds()).thenReturn(900L);
        lenient().when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        when(authUserRepository.saveAndFlush(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        AuthResponse response = authService.register(request);

        ArgumentCaptor<AuthUser> userCaptor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).saveAndFlush(userCaptor.capture());
        AuthUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("customer@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getRoles()).containsExactly(Role.USER);
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("customer@example.com");
        assertThat(response.roles()).containsExactly(Role.USER);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(" Customer@Example.com ", "password123", "Customer Name");
        when(authUserRepository.existsByEmailIgnoreCase("customer@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessage("Email is already registered");
        verify(authUserRepository, never()).saveAndFlush(any(AuthUser.class));
        verify(authUserRepository, never()).save(any(AuthUser.class));
        verify(jwtTokenService, never()).issueToken(any(AuthUser.class));
    }

    @Test
    void registerTranslatesDatabaseDuplicateEmailRaceAndDoesNotIssueToken() {
        RegisterRequest request = new RegisterRequest(" Customer@Example.com ", "password123", "Customer Name");
        when(authUserRepository.existsByEmailIgnoreCase("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(authUserRepository.saveAndFlush(any(AuthUser.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessage("Email is already registered");
        verify(jwtTokenService, never()).issueToken(any(AuthUser.class));
        verify(authUserRepository, never()).save(any(AuthUser.class));
    }

    @Test
    void loginForUnknownEmailStillPerformsOnePasswordCheckAndRejects() {
        LoginRequest request = new LoginRequest("missing@example.com", "password123");
        when(authUserRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("password123", AuthService.DUMMY_PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password");
        verify(passwordEncoder).matches("password123", AuthService.DUMMY_PASSWORD_HASH);
        verify(jwtTokenService, never()).issueToken(any(AuthUser.class));
    }

    @Test
    void loginForDisabledUserStillPerformsOnePasswordCheckAndRejects() {
        LoginRequest request = new LoginRequest("customer@example.com", "password123");
        AuthUser user = AuthUser.create("customer@example.com", "encoded-password", Set.of(Role.USER));
        ReflectionTestUtils.setField(user, "enabled", false);
        when(authUserRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", AuthService.DUMMY_PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password");
        verify(passwordEncoder).matches("password123", AuthService.DUMMY_PASSWORD_HASH);
        verify(jwtTokenService, never()).issueToken(any(AuthUser.class));
    }

    @Test
    void dummyPasswordHashIsValidBCryptHash() {
        assertThat(AuthService.DUMMY_PASSWORD_HASH)
            .hasSize(60)
            .matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
        assertThat(new BCryptPasswordEncoder().matches("dummy-password", AuthService.DUMMY_PASSWORD_HASH)).isTrue();
    }

    @Test
    void loginReturnsTokenForMatchingPassword() {
        LoginRequest request = new LoginRequest(" Customer@Example.com ", "password123");
        AuthUser user = AuthUser.create("customer@example.com", "encoded-password", Set.of(Role.USER));
        ReflectionTestUtils.setField(user, "id", 7L);
        when(authUserRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtTokenService.issueToken(user)).thenReturn("jwt-token");
        when(jwtTokenService.expiresInSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.email()).isEqualTo("customer@example.com");
        assertThat(response.roles()).containsExactly(Role.USER);
    }

    @Test
    void loginRejectsWrongPassword() {
        LoginRequest request = new LoginRequest("customer@example.com", "wrong-password");
        AuthUser user = AuthUser.create("customer@example.com", "encoded-password", Set.of(Role.USER));
        when(authUserRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password");
        verify(jwtTokenService, never()).issueToken(any(AuthUser.class));
    }

    @Test
    void loginRejectsUnknownEmail() {
        LoginRequest request = new LoginRequest("missing@example.com", "password123");
        when(authUserRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("password123", AuthService.DUMMY_PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password");
        verify(passwordEncoder).matches("password123", AuthService.DUMMY_PASSWORD_HASH);
        verify(jwtTokenService, never()).issueToken(any(AuthUser.class));
    }
}
