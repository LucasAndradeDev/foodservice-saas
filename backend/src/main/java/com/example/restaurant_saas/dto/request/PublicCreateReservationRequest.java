package com.example.restaurant_saas.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PublicCreateReservationRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String customerName;

    @NotBlank(message = "Phone is required")
    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String customerPhone;

    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;

    @NotNull(message = "Party size is required")
    @Positive(message = "Party size must be greater than zero")
    @Max(value = 50, message = "Party size must be at most 50")
    private Integer partySize;

    @NotNull(message = "Reservation time is required")
    @Future(message = "Reservation time must be in the future")
    private OffsetDateTime reservationTime;
}
