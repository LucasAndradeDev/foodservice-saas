package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.EmailVerificationToken;
import com.example.restaurant_saas.domain.entity.PasswordResetToken;
import com.example.restaurant_saas.domain.entity.RefreshToken;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.exception.RestaurantSuspendedException;
import com.example.restaurant_saas.dto.request.ChangePasswordRequest;
import com.example.restaurant_saas.dto.request.ForgotPasswordRequest;
import com.example.restaurant_saas.dto.request.LoginRequest;
import com.example.restaurant_saas.dto.request.RefreshTokenRequest;
import com.example.restaurant_saas.dto.request.RegisterRestaurantRequest;
import com.example.restaurant_saas.dto.request.ResetPasswordRequest;
import com.example.restaurant_saas.dto.request.VerifyEmailRequest;
import com.example.restaurant_saas.dto.response.AuthResponse;
import com.example.restaurant_saas.dto.response.RestaurantResponse;
import com.example.restaurant_saas.dto.response.UserResponse;
import com.example.restaurant_saas.repository.EmailVerificationTokenRepository;
import com.example.restaurant_saas.repository.PasswordResetTokenRepository;
import com.example.restaurant_saas.repository.RefreshTokenRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.UserRepository;
import com.example.restaurant_saas.security.JwtService;
import com.example.restaurant_saas.security.RateLimitService;
import com.example.restaurant_saas.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String LOGIN_ACTION = "login";
    private static final String FORGOT_PASSWORD_ACTION = "forgot-password";
    private static final String RESEND_VERIFICATION_ACTION = "resend-verification";

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RateLimitService rateLimitService;
    private final EmailService emailService;
    private final HttpServletRequest httpRequest;
    private final TenantActivator tenantActivator;

    @Value("${api.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${security.login-rate-limit.max-attempts}")
    private int loginMaxAttempts;

    @Value("${security.login-rate-limit.window-minutes}")
    private long loginWindowMinutes;

    @Value("${security.login-rate-limit.block-minutes}")
    private long loginBlockMinutes;

    @Value("${security.forgot-password-rate-limit.max-attempts}")
    private int forgotPasswordMaxAttempts;

    @Value("${security.forgot-password-rate-limit.window-minutes}")
    private long forgotPasswordWindowMinutes;

    @Value("${security.forgot-password-rate-limit.block-minutes}")
    private long forgotPasswordBlockMinutes;

    @Value("${security.password-reset-token.expiration-minutes}")
    private long passwordResetTokenExpirationMinutes;

    @Value("${security.email-verification-token.expiration-minutes}")
    private long emailVerificationTokenExpirationMinutes;

    @Value("${security.resend-verification-rate-limit.max-attempts}")
    private int resendVerificationMaxAttempts;

    @Value("${security.resend-verification-rate-limit.window-minutes}")
    private long resendVerificationWindowMinutes;

    @Value("${security.resend-verification-rate-limit.block-minutes}")
    private long resendVerificationBlockMinutes;

    @Transactional
    public AuthResponse registerRestaurant(RegisterRestaurantRequest request) {
        if (userRepository.existsByEmailBypassingRls(request.getOwnerEmail())) {
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

        tenantActivator.activate(restaurant.getId());
        try {
            User owner = User.builder()
                    .restaurant(restaurant)
                    .name(request.getOwnerName())
                    .email(request.getOwnerEmail().toLowerCase().trim())
                    .password(passwordEncoder.encode(request.getOwnerPassword()))
                    .role(UserRole.OWNER)
                    .active(true)
                    .emailVerified(false)
                    .termsAcceptedAt(request.isTermsAccepted() ? OffsetDateTime.now() : null)
                    .build();
            owner = userRepository.save(owner);

            sendVerificationEmail(owner);

            UserDetailsImpl userDetails = new UserDetailsImpl(owner);
            String accessToken = jwtService.generateToken(userDetails);
            RefreshToken refreshToken = createRefreshToken(owner);

            return buildAuthResponse(accessToken, refreshToken.getToken(), owner, restaurant);
        } finally {
            tenantActivator.deactivate();
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        rateLimitService.checkAllowed(LOGIN_ACTION, httpRequest, email);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (AuthenticationException ex) {
            rateLimitService.recordAttempt(LOGIN_ACTION, httpRequest, email, loginMaxAttempts, loginWindowMinutes, loginBlockMinutes);
            throw ex;
        }

        rateLimitService.reset(LOGIN_ACTION, httpRequest, email);

        User user = userRepository.findByEmailBypassingRls(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));

        if (!Boolean.TRUE.equals(user.getRestaurant().getActive())) {
            throw new RestaurantSuspendedException("Restaurant access suspended. Contact support.");
        }

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

        // token.getUser() is a lazy proxy whose id is already populated from the FK column (no
        // query yet), but actually loading the row (restaurant, active) needs the same RLS
        // bypass as above - nothing has set app.tenant_id at this point either.
        User user = userRepository.findByIdBypassingRls(token.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!Boolean.TRUE.equals(user.getRestaurant().getActive())) {
            throw new RestaurantSuspendedException("Restaurant access suspended. Contact support.");
        }

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

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        rateLimitService.checkAllowed(FORGOT_PASSWORD_ACTION, httpRequest, email);
        // Count every call, not just successful ones, so it can't be used to enumerate
        // registered emails by comparing rate-limit behavior across requests.
        rateLimitService.recordAttempt(
                FORGOT_PASSWORD_ACTION, httpRequest, email,
                forgotPasswordMaxAttempts, forgotPasswordWindowMinutes, forgotPasswordBlockMinutes
        );

        userRepository.findByEmailBypassingRls(email).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUser(user);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(UUID.randomUUID().toString())
                    .expiryDate(OffsetDateTime.now().plusMinutes(passwordResetTokenExpirationMinutes))
                    .used(false)
                    .build();
            resetToken = passwordResetTokenRepository.save(resetToken);

            String resetLink = frontendUrl + "/reset-password?token=" + resetToken.getToken();
            try {
                emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            } catch (RuntimeException ex) {
                // Swallow send failures: letting them propagate would turn a provider
                // outage into a way to tell registered emails apart from unregistered
                // ones (this branch only runs for registered emails, so an error here
                // would surface as a 500 instead of the same generic response).
                log.error("Failed to send password reset email to {}", user.getEmail(), ex);
            }
        });

        // Always the same outcome regardless of whether the email is registered,
        // so the response can't be used to discover which emails have accounts.
    }

    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link."));

        if (Boolean.TRUE.equals(resetToken.getUsed()) || resetToken.getExpiryDate().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired reset link.");
        }

        // resetToken.getUser() is a lazy proxy with only its id populated (no query yet); loading
        // the actual row needs the same RLS bypass as login/refreshToken above, since nothing has
        // set app.tenant_id at this point either. Restaurant itself carries no RLS, so it's safe
        // to read user.getRestaurant().getId() before the tenant is set, to then set it for the
        // save() calls below (which - unlike a bypass read - do go through the normal RLS-checked
        // path and need app.tenant_id to match).
        User user = userRepository.findByIdBypassingRls(resetToken.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        tenantActivator.activate(user.getRestaurant().getId());
        try {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);

            try {
                emailService.sendPasswordChangedNotification(user.getEmail());
            } catch (RuntimeException ex) {
                // The password was already changed successfully; a failed notification
                // shouldn't fail the whole request.
                log.error("Failed to send password changed notification to {}", user.getEmail(), ex);
            }

            // Force re-login everywhere: whoever reset the password might not be the
            // same person still holding an old session on another device.
            refreshTokenRepository.deleteByUser(user);
            RefreshToken refreshToken = createRefreshToken(user);

            UserDetailsImpl userDetails = new UserDetailsImpl(user);
            String accessToken = jwtService.generateToken(userDetails);

            return buildAuthResponse(accessToken, refreshToken.getToken(), user, user.getRestaurant());
        } finally {
            tenantActivator.deactivate();
        }
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification link."));

        if (Boolean.TRUE.equals(verificationToken.getUsed()) || verificationToken.getExpiryDate().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired verification link.");
        }

        User user = userRepository.findByIdBypassingRls(verificationToken.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        tenantActivator.activate(user.getRestaurant().getId());
        try {
            user.setEmailVerified(true);
            userRepository.save(user);

            verificationToken.setUsed(true);
            emailVerificationTokenRepository.save(verificationToken);
        } finally {
            tenantActivator.deactivate();
        }
    }

    @Transactional
    public void resendVerificationEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        rateLimitService.checkAllowed(RESEND_VERIFICATION_ACTION, httpRequest, user.getEmail());
        rateLimitService.recordAttempt(
                RESEND_VERIFICATION_ACTION, httpRequest, user.getEmail(),
                resendVerificationMaxAttempts, resendVerificationWindowMinutes, resendVerificationBlockMinutes
        );

        sendVerificationEmail(user);
    }

    private void sendVerificationEmail(User user) {
        emailVerificationTokenRepository.deleteByUser(user);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(OffsetDateTime.now().plusMinutes(emailVerificationTokenExpirationMinutes))
                .used(false)
                .build();
        verificationToken = emailVerificationTokenRepository.save(verificationToken);

        String verifyLink = frontendUrl + "/verify-email?token=" + verificationToken.getToken();
        try {
            emailService.sendVerificationEmail(user.getEmail(), verifyLink);
        } catch (RuntimeException ex) {
            // Account creation (or the resend action) already succeeded; a failed send
            // shouldn't fail the whole request. The user can trigger another resend.
            log.error("Failed to send verification email to {}", user.getEmail(), ex);
        }
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
                .emailVerified(user.getEmailVerified())
                .termsAcceptedAt(user.getTermsAcceptedAt())
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
                .paymentDueDate(restaurant.getPaymentDueDate())
                .autoPrintKitchenTickets(restaurant.getAutoPrintKitchenTickets())
                .kitchenWarningThresholdMinutes(restaurant.getKitchenWarningThresholdMinutes())
                .kitchenCriticalThresholdMinutes(restaurant.getKitchenCriticalThresholdMinutes())
                .tableForgottenWarningThresholdMinutes(restaurant.getTableForgottenWarningThresholdMinutes())
                .tableForgottenCriticalThresholdMinutes(restaurant.getTableForgottenCriticalThresholdMinutes())
                .serviceChargeEnabled(restaurant.getServiceChargeEnabled())
                .serviceChargePercentage(restaurant.getServiceChargePercentage())
                .reservationBlockBeforeMinutes(restaurant.getReservationBlockBeforeMinutes())
                .reservationBlockAfterMinutes(restaurant.getReservationBlockAfterMinutes())
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
