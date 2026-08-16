package com.kenshin.animetrackerserver.controller;

import com.kenshin.animetrackerserver.dto.request.auth.LoginRequest;
import com.kenshin.animetrackerserver.dto.request.auth.RegisterRequest;
import com.kenshin.animetrackerserver.dto.response.auth.RegisterAndAuthResponse;
import com.kenshin.animetrackerserver.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public RegisterAndAuthResponse register(
            @Valid @RequestBody RegisterRequest request
            ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public RegisterAndAuthResponse login(
            @Valid @RequestBody LoginRequest request
            ) {
        return authService.login(request);
    }
}
