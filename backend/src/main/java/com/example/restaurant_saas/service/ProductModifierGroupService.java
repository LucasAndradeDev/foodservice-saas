package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.ProductModifierGroup;
import com.example.restaurant_saas.domain.entity.ProductModifierOption;
import com.example.restaurant_saas.dto.request.CreateModifierGroupRequest;
import com.example.restaurant_saas.dto.request.ModifierOptionInput;
import com.example.restaurant_saas.dto.request.UpdateModifierGroupRequest;
import com.example.restaurant_saas.dto.response.ModifierGroupResponse;
import com.example.restaurant_saas.dto.response.ModifierOptionResponse;
import com.example.restaurant_saas.repository.ProductModifierGroupRepository;
import com.example.restaurant_saas.repository.ProductModifierOptionRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductModifierGroupService {

    private final ProductModifierGroupRepository groupRepository;
    private final ProductModifierOptionRepository optionRepository;
    private final ProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public List<ModifierGroupResponse> listGroups(UUID restaurantId, UUID productId) {
        findProduct(restaurantId, productId);
        List<ProductModifierGroup> groups = groupRepository.findByProductIdAndRestaurantIdOrderByCreatedAtAsc(productId, restaurantId);
        Map<UUID, List<ProductModifierOption>> optionsByGroup = fetchOptionsByGroup(groups);
        return groups.stream().map(group -> toResponse(group, optionsByGroup.getOrDefault(group.getId(), List.of()))).toList();
    }

    @Transactional
    public ModifierGroupResponse createGroup(UUID restaurantId, UUID productId, CreateModifierGroupRequest request) {
        Product product = findProduct(restaurantId, productId);

        ProductModifierGroup group = ProductModifierGroup.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .product(product)
                .name(request.getName())
                .selectionType(request.getSelectionType())
                .required(request.getRequired())
                .build();
        group = groupRepository.save(group);

        List<ProductModifierOption> options = optionRepository.saveAll(buildOptions(restaurantId, group, request.getOptions()));

        return toResponse(group, options);
    }

    @Transactional
    public ModifierGroupResponse updateGroup(UUID restaurantId, UUID productId, UUID groupId, UpdateModifierGroupRequest request) {
        ProductModifierGroup group = findGroup(restaurantId, productId, groupId);

        if (request.getName() != null) {
            group.setName(request.getName());
        }
        if (request.getSelectionType() != null) {
            group.setSelectionType(request.getSelectionType());
        }
        if (request.getRequired() != null) {
            group.setRequired(request.getRequired());
        }
        group = groupRepository.save(group);

        List<ProductModifierOption> options;
        if (request.getOptions() != null) {
            optionRepository.deleteByGroupId(group.getId());
            options = optionRepository.saveAll(buildOptions(restaurantId, group, request.getOptions()));
        } else {
            options = optionRepository.findByGroupIdOrderByCreatedAtAsc(group.getId());
        }

        return toResponse(group, options);
    }

    @Transactional
    public void deleteGroup(UUID restaurantId, UUID productId, UUID groupId) {
        ProductModifierGroup group = findGroup(restaurantId, productId, groupId);
        groupRepository.delete(group);
    }

    private List<ProductModifierOption> buildOptions(UUID restaurantId, ProductModifierGroup group, List<ModifierOptionInput> inputs) {
        return inputs.stream()
                .map(input -> ProductModifierOption.builder()
                        .restaurant(restaurantRepository.getReferenceById(restaurantId))
                        .group(group)
                        .name(input.getName())
                        .priceDelta(input.getPriceDelta())
                        .build())
                .toList();
    }

    private Map<UUID, List<ProductModifierOption>> fetchOptionsByGroup(List<ProductModifierGroup> groups) {
        if (groups.isEmpty()) {
            return Map.of();
        }
        List<UUID> groupIds = groups.stream().map(ProductModifierGroup::getId).toList();
        return optionRepository.findByGroupIdInOrderByCreatedAtAsc(groupIds).stream()
                .collect(Collectors.groupingBy(option -> option.getGroup().getId()));
    }

    private Product findProduct(UUID restaurantId, UUID productId) {
        return productRepository.findByIdAndRestaurantId(productId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
    }

    private ProductModifierGroup findGroup(UUID restaurantId, UUID productId, UUID groupId) {
        return groupRepository.findByIdAndProductIdAndRestaurantId(groupId, productId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Modifier group not found."));
    }

    private ModifierGroupResponse toResponse(ProductModifierGroup group, List<ProductModifierOption> options) {
        return ModifierGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .selectionType(group.getSelectionType())
                .required(group.getRequired())
                .options(options.stream().map(this::toOptionResponse).toList())
                .build();
    }

    private ModifierOptionResponse toOptionResponse(ProductModifierOption option) {
        return ModifierOptionResponse.builder()
                .id(option.getId())
                .name(option.getName())
                .priceDelta(option.getPriceDelta())
                .build();
    }
}
