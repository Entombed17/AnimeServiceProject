package com.kenshin.animetrackerserver.dto.response.auth;

import com.kenshin.animetrackerserver.dto.common.UserResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RegisterAndAuthResponse {

    private String accessToken;

    private String refreshToken;

    private UserResponse user;
}
