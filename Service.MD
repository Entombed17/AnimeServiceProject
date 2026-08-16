
    @Override
    public AccessTokenResponse refresh(RefreshTokenRequest request) {

    }

    @Override
    public void logout(RefreshTokenRequest request) {

    }


package com.kenshin.animetrackerserver.service;

import com.kenshin.animetrackerserver.dto.request.auth.LoginRequest;
import com.kenshin.animetrackerserver.dto.request.auth.RefreshTokenRequest;
import com.kenshin.animetrackerserver.dto.request.auth.RegisterRequest;
import com.kenshin.animetrackerserver.dto.response.auth.AccessTokenResponse;
import com.kenshin.animetrackerserver.dto.response.auth.RegisterAndAuthResponse;

public interface AuthService {

    RegisterAndAuthResponse register(RegisterRequest request);

    RegisterAndAuthResponse login(LoginRequest request);

    AccessTokenResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);
}
