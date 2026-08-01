package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.ComboItem;
import com.example.restaurant_saas.domain.entity.ComboSlot;
import com.example.restaurant_saas.domain.entity.ComboSlotOption;
import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.Product;
import com.example.restaurant_saas.domain.entity.ProductAvailabilityWindow;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.dto.request.ComboSlotSelectionInput;
import com.example.restaurant_saas.dto.request.CreateOrderItemRequest;
import com.example.restaurant_saas.repository.ComboItemRepository;
import com.example.restaurant_saas.repository.ComboSlotOptionRepository;
import com.example.restaurant_saas.repository.ComboSlotRepository;
import com.example.restaurant_saas.repository.ProductAvailabilityWindowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Expands a combo product into a "header" OrderItem plus one child OrderItem per fixed item /
 * chosen slot option, resolving each component to a real Product at its current price.
 */
@Service
@RequiredArgsConstructor
public class ComboExplodeService {

    private final ComboItemRepository comboItemRepository;
    private final ComboSlotRepository comboSlotRepository;
    private final ComboSlotOptionRepository comboSlotOptionRepository;
    private final ProductAvailabilityWindowRepository availabilityWindowRepository;

    private record ComponentLine(Product product, Integer quantity) {
    }

    OrderItem explodeUnit(UUID restaurantId, Order order, Product comboProduct, CreateOrderItemRequest itemRequest) {
        List<ComboItem> fixedItems = comboItemRepository
                .findByComboProductIdAndRestaurantIdOrderByCreatedAtAsc(comboProduct.getId(), restaurantId);
        List<ComboSlot> slots = comboSlotRepository
                .findByComboProductIdAndRestaurantIdOrderByCreatedAtAsc(comboProduct.getId(), restaurantId);

        List<UUID> slotIds = slots.stream().map(ComboSlot::getId).toList();
        Map<UUID, List<ComboSlotOption>> optionsBySlot = slotIds.isEmpty()
                ? Map.of()
                : comboSlotOptionRepository.findBySlotIdInOrderByCreatedAtAsc(slotIds).stream()
                        .collect(Collectors.groupingBy(option -> option.getSlot().getId()));

        List<ComboSlotSelectionInput> selections = itemRequest.getSlotSelections() == null
                ? List.of() : itemRequest.getSlotSelections();
        Map<UUID, UUID> selectedProductBySlot = new HashMap<>();
        for (ComboSlotSelectionInput selection : selections) {
            if (selectedProductBySlot.put(selection.getSlotId(), selection.getSelectedProductId()) != null) {
                throw new IllegalArgumentException("Duplicate selection for the same slot.");
            }
        }

        List<ComponentLine> lines = new ArrayList<>();
        for (ComboItem fixedItem : fixedItems) {
            lines.add(new ComponentLine(fixedItem.getComponentProduct(), fixedItem.getQuantity()));
        }
        for (ComboSlot slot : slots) {
            UUID selectedProductId = selectedProductBySlot.remove(slot.getId());
            if (selectedProductId == null) {
                if (Boolean.TRUE.equals(slot.getRequired())) {
                    throw new IllegalArgumentException("Select an option for " + slot.getName() + ".");
                }
                continue;
            }
            List<ComboSlotOption> options = optionsBySlot.getOrDefault(slot.getId(), List.of());
            ComboSlotOption chosen = options.stream()
                    .filter(option -> option.getProduct().getId().equals(selectedProductId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Selected option is not valid for slot " + slot.getName() + "."));
            lines.add(new ComponentLine(chosen.getProduct(), chosen.getQuantity()));
        }
        if (!selectedProductBySlot.isEmpty()) {
            throw new IllegalArgumentException("Selection references a slot that does not belong to this combo.");
        }

        for (ComponentLine line : lines) {
            if (!Boolean.TRUE.equals(line.product().getActive())) {
                throw new IllegalStateException("Product " + line.product().getName() + " is not active.");
            }
            if (!isAvailableNow(restaurantId, line.product())) {
                throw new IllegalStateException("Product " + line.product().getName() + " is not available at this time.");
            }
        }

        BigDecimal unitPrice = lines.stream()
                .map(line -> line.product().getPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean allHaveCostPrice = lines.stream().allMatch(line -> line.product().getCostPrice() != null);
        BigDecimal unitCostPrice = allHaveCostPrice
                ? lines.stream()
                        .map(line -> line.product().getCostPrice().multiply(BigDecimal.valueOf(line.quantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;

        OrderItem header = OrderItem.builder()
                .order(order)
                .product(comboProduct)
                .quantity(1)
                .unitPrice(unitPrice)
                .unitCostPrice(unitCostPrice)
                .observation(itemRequest.getObservation())
                .status(ItemStatus.PENDING)
                .discountType(comboProduct.getDiscountPercentage() != null ? DiscountType.PERCENTAGE : null)
                .discountValue(comboProduct.getDiscountPercentage())
                .build();

        List<OrderItem> children = lines.stream()
                .map(line -> OrderItem.builder()
                        .order(order)
                        .product(line.product())
                        .quantity(line.quantity())
                        .unitPrice(line.product().getPrice())
                        .unitCostPrice(line.product().getCostPrice())
                        .status(ItemStatus.PENDING)
                        .parentOrderItem(header)
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
        header.setChildren(children);

        return header;
    }

    private boolean isAvailableNow(UUID restaurantId, Product product) {
        List<ProductAvailabilityWindow> windows = availabilityWindowRepository
                .findByProductIdAndRestaurantIdOrderByCreatedAtAsc(product.getId(), restaurantId);
        if (windows.isEmpty()) {
            return true;
        }

        ZonedDateTime now = OffsetDateTime.now().atZoneSameInstant(ZoneId.systemDefault());
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        return windows.stream().anyMatch(window ->
                (window.getDayOfWeek() == null || window.getDayOfWeek() == today)
                        && !timeNow.isBefore(window.getStartTime())
                        && !timeNow.isAfter(window.getEndTime()));
    }
}
