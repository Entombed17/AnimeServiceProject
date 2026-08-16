package com.kenshin.animetrackerserver.dto.response.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccessTokenResponse {

    private String accessToken;
}
