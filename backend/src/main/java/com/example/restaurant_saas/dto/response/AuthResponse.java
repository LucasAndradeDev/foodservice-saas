package com.example.restaurant_saas.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    // Never sent to the client - AuthController reads this to set the httpOnly refresh-token
    // cookie, then the field is dropped before serialization. Keeping the refresh token out of
    // the JSON body (and therefore out of anywhere JS on the page could read it) is the whole
    // point of moving it to a cookie: an XSS payload can no longer exfiltrate it.
    @JsonIgnore
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private UserResponse user;
    private RestaurantResponse restaurant;
}
