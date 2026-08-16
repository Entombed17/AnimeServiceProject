package com.kenshin.animetrackerserver.service.impl;

import com.kenshin.animetrackerserver.dto.common.UserResponse;
import com.kenshin.animetrackerserver.dto.request.auth.LoginRequest;
import com.kenshin.animetrackerserver.dto.request.auth.RefreshTokenRequest;
import com.kenshin.animetrackerserver.dto.request.auth.RegisterRequest;
import com.kenshin.animetrackerserver.dto.response.auth.AccessTokenResponse;
import com.kenshin.animetrackerserver.dto.response.auth.RegisterAndAuthResponse;
import com.kenshin.animetrackerserver.entity.RefreshToken;
import com.kenshin.animetrackerserver.entity.User;
import com.kenshin.animetrackerserver.repository.RefreshTokenRepository;
import com.kenshin.animetrackerserver.repository.UserRepository;
import com.kenshin.animetrackerserver.service.AuthService;
import com.kenshin.animetrackerserver.service.JwtService;
import com.kenshin.animetrackerserver.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final RefreshTokenService refreshTokenService;

    @Override
    public RegisterAndAuthResponse register(RegisterRequest request) {
        if(userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if(userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(savedUser);

        RefreshToken refreshToken = refreshTokenService.generateRefreshToken(savedUser);

        UserResponse userResponse = new UserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail()
        );

        return new RegisterAndAuthResponse(
                accessToken,
                refreshToken.getToken(),
                userResponse
        );
    }

    @Override
    public RegisterAndAuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

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
}
