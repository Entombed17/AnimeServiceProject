package com.kenshin.animetrackerserver.dto.response.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordResponse {

    private boolean success;
}
