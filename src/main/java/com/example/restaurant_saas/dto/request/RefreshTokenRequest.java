package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "O refresh token é obrigatório")
    private String refreshToken;
}
