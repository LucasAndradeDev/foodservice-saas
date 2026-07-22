package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRestaurantRequest {

    // Dados do Restaurante
    @NotBlank(message = "O nome do restaurante é obrigatório")
    private String restaurantName;

    private String cnpj;
    private String phone;
    private String address;

    // Dados do Proprietário (OWNER)
    @NotBlank(message = "O nome do proprietário é obrigatório")
    private String ownerName;

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    private String ownerEmail;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres")
    private String ownerPassword;
}
