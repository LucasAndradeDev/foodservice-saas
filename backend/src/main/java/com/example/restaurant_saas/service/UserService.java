package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.PasswordResetToken;
import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateUserRequest;
import com.example.restaurant_saas.dto.request.UpdateUserRequest;
import com.example.restaurant_saas.dto.response.UserResponse;
import com.example.restaurant_saas.repository.PasswordResetTokenRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Map<UserRole, Set<UserRole>> ASSIGNABLE_ROLES = Map.of(
            UserRole.OWNER, EnumSet.of(UserRole.MANAGER, UserRole.WAITER, UserRole.KITCHEN, UserRole.CASHIER),
            UserRole.MANAGER, EnumSet.of(UserRole.WAITER, UserRole.KITCHEN, UserRole.CASHIER)
    );

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${security.password-reset-token.expiration-minutes}")
    private long passwordResetTokenExpirationMinutes;

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID restaurantId, UserRole roleFilter, Boolean activeFilter) {
        return userRepository.findByRestaurantId(restaurantId).stream()
                .filter(user -> roleFilter == null || user.getRole() == roleFilter)
                .filter(user -> activeFilter == null || activeFilter.equals(user.getActive()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID restaurantId, UUID userId) {
        return toResponse(findByIdAndRestaurant(restaurantId, userId));
    }

    @Transactional
    public UserResponse createUser(UUID restaurantId, UserRole actingRole, CreateUserRequest request) {
        if (!assignableRoles(actingRole).contains(request.getRole())) {
            throw new IllegalStateException("You do not have permission to create a user with role " + request.getRole() + ".");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered.");
        }

        // No password is collected here: the account starts with a random, never-shared
        // password and an invite email carrying a set-password link, same token mechanism
        // as the forgot-password flow (see sendPasswordSetupEmail below).
        User user = User.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .name(request.getName())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(request.getRole())
                .active(true)
                .build();

        user = userRepository.save(user);
        sendPasswordSetupEmail(user, true);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID restaurantId, UUID actingUserId, UserRole actingRole, UUID targetUserId, UpdateUserRequest request) {
        User target = findByIdAndRestaurant(restaurantId, targetUserId);

        if (targetUserId.equals(actingUserId) && (request.getRole() != null || request.getActive() != null)) {
            throw new IllegalStateException("You cannot change your own role or activation status.");
        }

        if (actingRole == UserRole.MANAGER && !assignableRoles(UserRole.MANAGER).contains(target.getRole())) {
            throw new IllegalStateException("You do not have permission to manage this user.");
        }

        if (request.getRole() != null && !assignableRoles(actingRole).contains(request.getRole())) {
            throw new IllegalStateException("You do not have permission to assign role " + request.getRole() + ".");
        }

        if (Boolean.FALSE.equals(request.getActive()) && target.getRole() == UserRole.OWNER) {
            long activeOwners = userRepository.countByRestaurantIdAndRoleAndActive(restaurantId, UserRole.OWNER, true);
            if (activeOwners <= 1) {
                throw new IllegalStateException("Cannot deactivate the restaurant's only active owner.");
            }
        }

        if (request.getName() != null) {
            target.setName(request.getName());
        }
        if (request.getRole() != null) {
            target.setRole(request.getRole());
        }
        if (request.getActive() != null) {
            target.setActive(request.getActive());
        }

        return toResponse(userRepository.save(target));
    }

    @Transactional
    public void sendPasswordResetLink(UUID restaurantId, UUID actingUserId, UserRole actingRole, UUID targetUserId) {
        User target = findByIdAndRestaurant(restaurantId, targetUserId);

        if (targetUserId.equals(actingUserId)) {
            throw new IllegalStateException("Use the change-password flow to update your own password.");
        }

        if (target.getRole() == UserRole.OWNER) {
            throw new IllegalStateException("Cannot reset an owner's password from the staff panel.");
        }

        if (actingRole == UserRole.MANAGER && !assignableRoles(UserRole.MANAGER).contains(target.getRole())) {
            throw new IllegalStateException("You do not have permission to manage this user.");
        }

        sendPasswordSetupEmail(target, false);
    }

    // Reused by both account creation (isNewAccount=true, "welcome" copy) and the staff
    // panel's resend action (isNewAccount=false, same copy as the self-service forgot-password
    // flow) -- same PasswordResetToken mechanism as AuthService.forgotPassword, just triggered
    // by an OWNER/MANAGER on someone else's behalf instead of by the user themselves.
    private void sendPasswordSetupEmail(User user, boolean isNewAccount) {
        passwordResetTokenRepository.deleteByUser(user);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(OffsetDateTime.now().plusMinutes(passwordResetTokenExpirationMinutes))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        String link = frontendUrl + "/reset-password?token=" + resetToken.getToken();
        try {
            if (isNewAccount) {
                emailService.sendStaffInviteEmail(user.getEmail(), link);
            } else {
                emailService.sendPasswordResetEmail(user.getEmail(), link);
            }
        } catch (RuntimeException ex) {
            log.error("Failed to send password setup email to {}", user.getEmail(), ex);
        }
    }

    private Set<UserRole> assignableRoles(UserRole actingRole) {
        return ASSIGNABLE_ROLES.getOrDefault(actingRole, Set.of());
    }

    private User findByIdAndRestaurant(UUID restaurantId, UUID userId) {
        return userRepository.findByIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .restaurantId(user.getRestaurant().getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.getActive())
                .emailVerified(user.getEmailVerified())
                .build();
    }
}
