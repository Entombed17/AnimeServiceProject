package com.kenshin.animetrackerserver.dto.request.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank
    @Size(min = 1, max = 20)
    private String username;

    @Size(max = 70)
    private String profileStatus;

    private LocalDate birthDate;

    private String avatarUrl;
}
