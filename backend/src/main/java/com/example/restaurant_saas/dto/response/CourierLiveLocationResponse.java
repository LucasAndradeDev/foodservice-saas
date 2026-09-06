package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

// Exact coordinates - only returned to authenticated staff (see DeliveryService#listLiveCouriers),
// unlike the fuzzed courierLatitude/courierLongitude on the public DeliveryDetailsResponse.
@Data
@Builder
public class CourierLiveLocationResponse {
    private UUID id;
    private String name;
    private Double latitude;
    private Double longitude;
    // False while they have a delivery currently OUT_FOR_DELIVERY with them - the "Livres/Todos"
    // filter on the staff map (DeliveryPage) is just this field client-side.
    private boolean available;
}
