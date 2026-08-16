package com.kenshin.animetrackerserver.dto.response.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateProfileResponse {

    private String email;

    private String username;

    private String profileStatus;

    private LocalDate birthDate;

    private String avatarUrl;
}
