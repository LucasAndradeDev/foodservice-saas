package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRestaurantRequest {

    @Size(max = 100, message = "O nome fantasia deve ter no máximo 100 caracteres")
    private String tradeName;

    @Size(max = 255, message = "A URL do logo deve ter no máximo 255 caracteres")
    private String logo;

    @Size(max = 20, message = "A cor principal deve ter no máximo 20 caracteres")
    private String primaryColor;

    @PositiveOrZero(message = "A quantidade de mesas não pode ser negativa")
    private Integer tableCount;

    @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
    private String phone;

    @Size(max = 255, message = "O endereço deve ter no máximo 255 caracteres")
    private String address;
}
