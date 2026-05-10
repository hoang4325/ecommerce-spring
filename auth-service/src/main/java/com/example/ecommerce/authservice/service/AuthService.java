package com.example.ecommerce.authservice.service;

import com.example.ecommerce.authservice.dto.AuthResponse;
import com.example.ecommerce.authservice.dto.LoginRequest;
import com.example.ecommerce.authservice.dto.RegisterRequest;
import com.example.ecommerce.authservice.entity.AuthUser;
import com.example.ecommerce.authservice.entity.Role;
import com.example.ecommerce.authservice.exception.DuplicateEmailException;
import com.example.ecommerce.authservice.exception.InvalidCredentialsException;
import com.example.ecommerce.authservice.repository.AuthUserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    static final String DUMMY_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhiY1t5FzI3L5LmFY9t7R1pG9p6Yw5U";

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
        AuthUserRepository authUserRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService
    ) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (authUserRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }

        AuthUser user = AuthUser.create(email, passwordEncoder.encode(request.password()), Set.of(Role.USER));
        AuthUser savedUser;
        try {
            savedUser = authUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEmailException();
        }

        return authResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Optional<AuthUser> foundUser = authUserRepository.findByEmailIgnoreCase(email);
        Optional<AuthUser> enabledUser = foundUser.filter(AuthUser::isEnabled);
        String passwordHash = enabledUser.map(AuthUser::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        AuthUser user = enabledUser
            .filter(ignored -> passwordMatches)
            .orElseThrow(InvalidCredentialsException::new);

        return authResponse(user);
    }

    private AuthResponse authResponse(AuthUser user) {
        return new AuthResponse(
            jwtTokenService.issueToken(user),
            TOKEN_TYPE,
            jwtTokenService.expiresInSeconds(),
            user.getId(),
            user.getEmail(),
            user.getRoles()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
