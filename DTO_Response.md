package com.kenshin.animetrackerserver.dto.response.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccessTokenResponse {

    @NotBlank
    private String accessToken;
}

package com.kenshin.animetrackerserver.dto.response.auth;

import com.kenshin.animetrackerserver.dto.common.UserResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterAndAuthResponse {

    @NotBlank
    private String accessToken;

    @NotBlank
    private String refreshToken;

    @NotBlank
    private UserResponse user;
}

package com.kenshin.animetrackerserver.dto.response.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordResponse {

    @NotBlank
    @Size(min = 8)
    private String newPassword;
}

package com.kenshin.animetrackerserver.dto.response.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateProfileResponse {

    @NotBlank
    @Size(min = 1, max = 20)
    private String username;

    @Size(max = 70)
    private String profileStatus;

    private LocalDate birthDate;

    private String avatarUrl;
}
