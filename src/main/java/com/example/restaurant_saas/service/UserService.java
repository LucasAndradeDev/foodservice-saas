package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.User;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.CreateUserRequest;
import com.example.restaurant_saas.dto.request.UpdateUserRequest;
import com.example.restaurant_saas.dto.response.UserResponse;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Map<UserRole, Set<UserRole>> ASSIGNABLE_ROLES = Map.of(
            UserRole.OWNER, EnumSet.of(UserRole.MANAGER, UserRole.WAITER, UserRole.KITCHEN, UserRole.CASHIER),
            UserRole.MANAGER, EnumSet.of(UserRole.WAITER, UserRole.KITCHEN, UserRole.CASHIER)
    );

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;

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
            throw new IllegalStateException("Você não tem permissão para criar um usuário com o papel " + request.getRole() + ".");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("E-mail já cadastrado no sistema.");
        }

        User user = User.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .name(request.getName())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .active(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(UUID restaurantId, UUID actingUserId, UserRole actingRole, UUID targetUserId, UpdateUserRequest request) {
        User target = findByIdAndRestaurant(restaurantId, targetUserId);

        if (targetUserId.equals(actingUserId) && (request.getRole() != null || request.getActive() != null)) {
            throw new IllegalStateException("Você não pode alterar o seu próprio papel ou status de ativação.");
        }

        if (actingRole == UserRole.MANAGER && !assignableRoles(UserRole.MANAGER).contains(target.getRole())) {
            throw new IllegalStateException("Você não tem permissão para gerenciar esse usuário.");
        }

        if (request.getRole() != null && !assignableRoles(actingRole).contains(request.getRole())) {
            throw new IllegalStateException("Você não tem permissão para atribuir o papel " + request.getRole() + ".");
        }

        if (Boolean.FALSE.equals(request.getActive()) && target.getRole() == UserRole.OWNER) {
            long activeOwners = userRepository.countByRestaurantIdAndRoleAndActive(restaurantId, UserRole.OWNER, true);
            if (activeOwners <= 1) {
                throw new IllegalStateException("Não é possível desativar o único proprietário ativo do restaurante.");
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

    private Set<UserRole> assignableRoles(UserRole actingRole) {
        return ASSIGNABLE_ROLES.getOrDefault(actingRole, Set.of());
    }

    private User findByIdAndRestaurant(UUID restaurantId, UUID userId) {
        return userRepository.findByIdAndRestaurantId(userId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .restaurantId(user.getRestaurant().getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.getActive())
                .build();
    }
}
