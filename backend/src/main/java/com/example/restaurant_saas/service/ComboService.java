package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.ComboItem;
import com.example.restaurant_saas.domain.entity.ComboSlot;
import com.example.restaurant_saas.domain.entity.ComboSlotOption;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.enums.ProductType;
import com.example.restaurant_saas.dto.request.ComboItemInput;
import com.example.restaurant_saas.dto.request.ComboSlotInput;
import com.example.restaurant_saas.dto.request.ComboSlotOptionInput;
import com.example.restaurant_saas.dto.request.UpdateComboCompositionRequest;
import com.example.restaurant_saas.dto.response.ComboCompositionResponse;
import com.example.restaurant_saas.dto.response.ComboItemResponse;
import com.example.restaurant_saas.dto.response.ComboSlotOptionResponse;
import com.example.restaurant_saas.dto.response.ComboSlotResponse;
import com.example.restaurant_saas.repository.ComboItemRepository;
import com.example.restaurant_saas.repository.ComboSlotOptionRepository;
import com.example.restaurant_saas.repository.ComboSlotRepository;
import com.example.restaurant_saas.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComboService {

    private final ProductRepository productRepository;
    private final ComboItemRepository comboItemRepository;
    private final ComboSlotRepository comboSlotRepository;
    private final ComboSlotOptionRepository comboSlotOptionRepository;

    @Transactional(readOnly = true)
    public ComboCompositionResponse getComposition(UUID restaurantId, UUID comboProductId) {
        Product combo = findCombo(restaurantId, comboProductId);
        List<ComboItem> fixedItems = comboItemRepository.findByComboProductIdAndRestaurantIdOrderByCreatedAtAsc(comboProductId, restaurantId);
        List<ComboSlot> slots = comboSlotRepository.findByComboProductIdAndRestaurantIdOrderByCreatedAtAsc(comboProductId, restaurantId);
        Map<UUID, List<ComboSlotOption>> optionsBySlot = loadOptionsBySlot(slots);
        return toResponse(combo, fixedItems, slots, optionsBySlot);
    }

    @Transactional
    public ComboCompositionResponse updateComposition(UUID restaurantId, UUID comboProductId, UpdateComboCompositionRequest request) {
        Product combo = findCombo(restaurantId, comboProductId);
        if (combo.getType() != ProductType.COMBO) {
            throw new IllegalArgumentException("Product is not a combo. Set its type to COMBO first.");
        }
        if (request.getFixedItems().isEmpty() && request.getSlots().isEmpty()) {
            throw new IllegalArgumentException("A combo must have at least one fixed item or slot.");
        }

        comboItemRepository.deleteByComboProductId(comboProductId);
        comboSlotRepository.deleteByComboProductId(comboProductId);

        List<ComboItem> fixedItems = new ArrayList<>();
        for (ComboItemInput input : request.getFixedItems()) {
            Product component = findComponent(restaurantId, combo, input.getProductId());
            fixedItems.add(ComboItem.builder()
                    .restaurant(combo.getRestaurant())
                    .comboProduct(combo)
                    .componentProduct(component)
                    .quantity(input.getQuantity())
                    .build());
        }
        fixedItems = comboItemRepository.saveAll(fixedItems);

        List<ComboSlot> slots = new ArrayList<>();
        Map<UUID, List<ComboSlotOption>> optionsBySlot = new HashMap<>();
        for (ComboSlotInput slotInput : request.getSlots()) {
            ComboSlot slot = comboSlotRepository.save(ComboSlot.builder()
                    .restaurant(combo.getRestaurant())
                    .comboProduct(combo)
                    .name(slotInput.getName())
                    .required(true)
                    .build());
            List<ComboSlotOption> options = new ArrayList<>();
            for (ComboSlotOptionInput optionInput : slotInput.getOptions()) {
                Product optionProduct = findComponent(restaurantId, combo, optionInput.getProductId());
                options.add(ComboSlotOption.builder()
                        .restaurant(combo.getRestaurant())
                        .slot(slot)
                        .product(optionProduct)
                        .quantity(optionInput.getQuantity())
                        .build());
            }
            options = comboSlotOptionRepository.saveAll(options);
            optionsBySlot.put(slot.getId(), options);
            slots.add(slot);
        }

        combo.setDiscountPercentage(request.getDiscountPercentage());
        combo = productRepository.save(combo);

        return toResponse(combo, fixedItems, slots, optionsBySlot);
    }

    private Product findComponent(UUID restaurantId, Product combo, UUID componentProductId) {
        Product component = productRepository.findByIdAndRestaurantId(componentProductId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Component product not found."));
        if (component.getId().equals(combo.getId())) {
            throw new IllegalArgumentException("A combo cannot reference itself.");
        }
        if (component.getType() == ProductType.COMBO) {
            throw new IllegalArgumentException("A combo cannot contain another combo.");
        }
        return component;
    }

    private Map<UUID, List<ComboSlotOption>> loadOptionsBySlot(List<ComboSlot> slots) {
        if (slots.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> slotIds = slots.stream().map(ComboSlot::getId).toList();
        return comboSlotOptionRepository.findBySlotIdInOrderByCreatedAtAsc(slotIds).stream()
                .collect(Collectors.groupingBy(option -> option.getSlot().getId()));
    }

    private Product findCombo(UUID restaurantId, UUID comboProductId) {
        return productRepository.findByIdAndRestaurantId(comboProductId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
    }

    private ComboCompositionResponse toResponse(Product combo, List<ComboItem> fixedItems, List<ComboSlot> slots,
                                                 Map<UUID, List<ComboSlotOption>> optionsBySlot) {
        List<ComboItemResponse> fixedItemResponses = fixedItems.stream()
                .map(item -> ComboItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getComponentProduct().getId())
                        .productName(item.getComponentProduct().getName())
                        .unitPrice(item.getComponentProduct().getPrice())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        List<ComboSlotResponse> slotResponses = slots.stream()
                .map(slot -> {
                    List<ComboSlotOptionResponse> optionResponses = optionsBySlot.getOrDefault(slot.getId(), List.of()).stream()
                            .map(option -> ComboSlotOptionResponse.builder()
                                    .id(option.getId())
                                    .productId(option.getProduct().getId())
                                    .productName(option.getProduct().getName())
                                    .unitPrice(option.getProduct().getPrice())
                                    .quantity(option.getQuantity())
                                    .build())
                            .toList();
                    return ComboSlotResponse.builder()
                            .id(slot.getId())
                            .name(slot.getName())
                            .required(slot.getRequired())
                            .options(optionResponses)
                            .build();
                })
                .toList();

        BigDecimal fixedTotal = fixedItems.stream()
                .map(item -> item.getComponentProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal slotsMin = BigDecimal.ZERO;
        BigDecimal slotsMax = BigDecimal.ZERO;
        for (ComboSlot slot : slots) {
            List<ComboSlotOption> options = optionsBySlot.getOrDefault(slot.getId(), List.of());
            BigDecimal min = null;
            BigDecimal max = null;
            for (ComboSlotOption option : options) {
                BigDecimal optionTotal = option.getProduct().getPrice().multiply(BigDecimal.valueOf(option.getQuantity()));
                if (min == null || optionTotal.compareTo(min) < 0) {
                    min = optionTotal;
                }
                if (max == null || optionTotal.compareTo(max) > 0) {
                    max = optionTotal;
                }
            }
            slotsMin = slotsMin.add(min == null ? BigDecimal.ZERO : min);
            slotsMax = slotsMax.add(max == null ? BigDecimal.ZERO : max);
        }

        BigDecimal discountPercentage = combo.getDiscountPercentage() == null ? BigDecimal.ZERO : combo.getDiscountPercentage();
        BigDecimal discountFactor = BigDecimal.valueOf(100).subtract(discountPercentage)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        BigDecimal minPrice = fixedTotal.add(slotsMin).multiply(discountFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxPrice = fixedTotal.add(slotsMax).multiply(discountFactor).setScale(2, RoundingMode.HALF_UP);

        return ComboCompositionResponse.builder()
                .productId(combo.getId())
                .discountPercentage(combo.getDiscountPercentage())
                .fixedItems(fixedItemResponses)
                .slots(slotResponses)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .build();
    }
}
