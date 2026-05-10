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
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

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
        AuthUser savedUser = authUserRepository.save(user);

        return authResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AuthUser user = authUserRepository.findByEmailIgnoreCase(email)
            .filter(AuthUser::isEnabled)
            .filter(foundUser -> passwordEncoder.matches(request.password(), foundUser.getPasswordHash()))
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
