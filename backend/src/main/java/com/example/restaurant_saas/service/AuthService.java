package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.RefreshToken;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.ChangePasswordRequest;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.dto.request.RefreshTokenRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.response.AuthResponse;
import com.example.restaurant_saas.dto.response.RestaurantResponse;
import com.example.restaurant_saas.dto.response.UserResponse;
import com.example.restaurant_saas.repository.RefreshTokenRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.LoginRateLimitService;
import com.example.restaurant_saas.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoginRateLimitService loginRateLimitService;
    private final HttpServletRequest httpRequest;

    @Value("${api.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse registerRestaurant(RegisterRestaurantRequest request) {
        if (userRepository.existsByEmail(request.getOwnerEmail())) {
            throw new IllegalArgumentException("Email already registered.");
        }

        if (request.getCnpj() != null && !request.getCnpj().isBlank() && restaurantRepository.existsByCnpj(request.getCnpj())) {
            throw new IllegalArgumentException("CNPJ already registered.");
        }

        Restaurant restaurant = Restaurant.builder()
                .name(request.getRestaurantName())
                .slug(generateUniqueSlug(request.getRestaurantName()))
                .cnpj(request.getCnpj())
                .phone(request.getPhone())
                .address(request.getAddress())
                .active(true)
                .build();
        restaurant = restaurantRepository.save(restaurant);

        User owner = User.builder()
                .restaurant(restaurant)
                .name(request.getOwnerName())
                .email(request.getOwnerEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getOwnerPassword()))
                .role(UserRole.OWNER)
                .active(true)
                .build();
        owner = userRepository.save(owner);

        UserDetailsImpl userDetails = new UserDetailsImpl(owner);
        String accessToken = jwtService.generateToken(userDetails);
        RefreshToken refreshToken = createRefreshToken(owner);

        return buildAuthResponse(accessToken, refreshToken.getToken(), owner, restaurant);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        loginRateLimitService.checkAllowed(httpRequest, email);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (AuthenticationException ex) {
            loginRateLimitService.recordFailure(httpRequest, email);
            throw ex;
        }

        loginRateLimitService.recordSuccess(httpRequest, email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String accessToken = jwtService.generateToken(userDetails);

        refreshTokenRepository.deleteByUser(user);
        RefreshToken refreshToken = createRefreshToken(user);

        return buildAuthResponse(accessToken, refreshToken.getToken(), user, user.getRestaurant());
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found."));

        if (Boolean.TRUE.equals(token.getRevoked()) || token.getExpiryDate().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Refresh token expired or revoked.");
        }

        User user = token.getUser();
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String newAccessToken = jwtService.generateToken(userDetails);

        return buildAuthResponse(newAccessToken, token.getToken(), user, user.getRestaurant());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        return buildAuthResponse(null, null, user, user.getRestaurant());
    }

    private String generateUniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;
        while (restaurantRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutAccents = normalized.replaceAll("\\p{M}", "");
        String slug = withoutAccents.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "restaurante" : slug;
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(OffsetDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user, Restaurant restaurant) {
        UserResponse userResp = UserResponse.builder()
                .id(user.getId())
                .restaurantId(restaurant.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.getActive())
                .build();

        RestaurantResponse restaurantResp = RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .tradeName(restaurant.getTradeName())
                .slug(restaurant.getSlug())
                .cnpj(restaurant.getCnpj())
                .phone(restaurant.getPhone())
                .address(restaurant.getAddress())
                .logo(restaurant.getLogo())
                .tableCount(restaurant.getTableCount())
                .active(restaurant.getActive())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(userResp)
                .restaurant(restaurantResp)
                .build();
    }
}
