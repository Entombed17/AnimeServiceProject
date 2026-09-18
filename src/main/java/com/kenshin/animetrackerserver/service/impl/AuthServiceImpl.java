package com.kenshin.animetrackerserver.service.impl;

import com.kenshin.animetrackerserver.dto.common.UserResponse;
import com.kenshin.animetrackerserver.dto.request.auth.LoginRequest;
import com.kenshin.animetrackerserver.dto.request.auth.RegisterRequest;
import com.kenshin.animetrackerserver.dto.response.auth.RegisterAndAuthResponse;
import com.kenshin.animetrackerserver.entity.RefreshToken;
import com.kenshin.animetrackerserver.entity.User;
import com.kenshin.animetrackerserver.exception.InvalidCredentialsException;
import com.kenshin.animetrackerserver.exception.UserAlreadyExistsException;
import com.kenshin.animetrackerserver.repository.UserRepository;
import com.kenshin.animetrackerserver.service.AuthService;
import com.kenshin.animetrackerserver.service.JwtService;
import com.kenshin.animetrackerserver.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public RegisterAndAuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        String username = request.getUsername().trim();

        if(userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username already exists");
        }

        if(userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        return buildAuthResponse(savedUser);
    }

    @Override
    @Transactional
    public RegisterAndAuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    private RegisterAndAuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);

        RefreshToken refreshToken = refreshTokenService.generateRefreshToken(user);

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );

        return new RegisterAndAuthResponse(
                accessToken,
                refreshToken.getToken(),
                userResponse
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
