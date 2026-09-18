package com.kenshin.animetrackerserver.service;

import com.kenshin.animetrackerserver.dto.request.auth.LoginRequest;
import com.kenshin.animetrackerserver.dto.request.auth.RegisterRequest;
import com.kenshin.animetrackerserver.dto.response.auth.RegisterAndAuthResponse;

public interface AuthService {

    RegisterAndAuthResponse register(RegisterRequest request);

    RegisterAndAuthResponse login(LoginRequest request);
}
