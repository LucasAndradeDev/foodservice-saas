package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.Payment;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.DiscountType;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.PaymentStatus;
import com.example.restaurant_saas.domain.enums.TabStatus;
import com.example.restaurant_saas.domain.enums.TableStatus;
import com.example.restaurant_saas.domain.enums.UserRole;
import com.example.restaurant_saas.dto.request.AddTableToTabRequest;
import com.example.restaurant_saas.dto.request.ApplyDiscountRequest;
import com.example.restaurant_saas.dto.request.MergeTabRequest;
import com.example.restaurant_saas.dto.request.OpenTabRequest;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.VoidPaymentRequest;
import com.example.restaurant_saas.dto.response.PaymentResponse;
import com.example.restaurant_saas.dto.response.TabResponse;
import com.example.restaurant_saas.dto.response.TabTableSummary;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.PaymentRepository;
import com.example.restaurant_saas.repository.ReservationRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import com.example.restaurant_saas.repository.TabRepository;
import com.example.restaurant_saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TabService {

    private final TabRepository tabRepository;
    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final CashRegisterService cashRegisterService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public List<TabResponse> listTabs(UUID restaurantId, TabStatus statusFilter) {
        return tabRepository.findByRestaurantId(restaurantId).stream()
                .filter(tab -> statusFilter == null || statusFilter.equals(tab.getStatus()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TabResponse getTab(UUID restaurantId, UUID tabId) {
        return toResponse(findByIdAndRestaurant(restaurantId, tabId));
    }

    @Transactional
    public TabResponse openTab(UUID restaurantId, OpenTabRequest request) {
        List<UUID> distinctIds = request.getTableIds().stream().distinct().toList();

        List<RestaurantTable> tables = tableRepository.findByIdInAndRestaurantId(distinctIds, restaurantId);
        if (tables.size() != distinctIds.size()) {
            throw new IllegalArgumentException("One or more tables were not found in this restaurant.");
        }

        for (RestaurantTable table : tables) {
            if (!Boolean.TRUE.equals(table.getActive())) {
                throw new IllegalStateException("Table " + table.getNumber() + " is not active.");
            }
            if (table.getStatus() != TableStatus.FREE) {
                throw new IllegalStateException("Table " + table.getNumber() + " is not free.");
            }
        }
        assertTablesNotReserved(tables, restaurantId);

        Tab tab = Tab.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .status(TabStatus.OPEN)
                .openedAt(OffsetDateTime.now())
                .tables(tables)
                .build();
        tab = tabRepository.save(tab);

        tables.forEach(table -> table.setStatus(TableStatus.OCCUPIED));
        tableRepository.saveAll(tables);

        return toResponse(tab);
    }

    public Tab openOrGetTabForTable(UUID restaurantId, UUID tableId) {
        return openOrGetTabForTable(restaurantId, tableId, null);
    }

    @Transactional
    public Tab openOrGetTabForTable(UUID restaurantId, UUID tableId, String customerPhone) {
        RestaurantTable table = tableRepository.findByIdAndRestaurantIdForUpdate(tableId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found."));
        if (!Boolean.TRUE.equals(table.getActive())) {
            throw new IllegalStateException("Table is not active.");
        }

        Optional<Tab> openTab = tabRepository.findOpenTabByRestaurantIdAndTableId(restaurantId, tableId);
        if (openTab.isPresent()) {
            return openTab.get();
        }

        if (table.getStatus() != TableStatus.FREE) {
            throw new IllegalStateException("Table is not available for self-ordering right now. Please call a waiter.");
        }
        assertTablesNotReserved(List.of(table), restaurantId);

        Tab tab = Tab.builder()
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .status(TabStatus.OPEN)
                .openedAt(OffsetDateTime.now())
                .customerPhone(customerPhone)
                // Must be a mutable list: Hibernate clears/replaces this collection on a later
                // merge of this same instance (e.g. a caller that re-saves the returned Tab in
                // the same transaction), which throws UnsupportedOperationException on List.of().
                .tables(new ArrayList<>(List.of(table)))
                .build();
        tab = tabRepository.save(tab);

        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        return tab;
    }

    @Transactional
    public TabResponse addTable(UUID restaurantId, UUID tabId, AddTableToTabRequest request) {
        Tab tab = findByIdAndRestaurant(restaurantId, tabId);
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }

        RestaurantTable table = tableRepository.findByIdAndRestaurantIdForUpdate(request.getTableId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found."));
        if (!Boolean.TRUE.equals(table.getActive())) {
            throw new IllegalStateException("Table is not active.");
        }
        if (table.getStatus() != TableStatus.FREE) {
            throw new IllegalStateException("Table " + table.getNumber() + " is not free.");
        }
        assertTablesNotReserved(List.of(table), restaurantId);

        tab.getTables().add(table);
        tabRepository.save(tab);

        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        return toResponse(tab);
    }

    @Transactional
    public TabResponse mergeTab(UUID restaurantId, UUID targetTabId, MergeTabRequest request) {
        if (targetTabId.equals(request.getSourceTabId())) {
            throw new IllegalArgumentException("A tab cannot be merged into itself.");
        }

        // Locked (not a plain read): both tabs are about to have their order sets mutated, which must
        // not race a concurrent registerPayments/applyDiscount call freezing either one's bill total.
        Tab target = tabRepository.findByIdAndRestaurantIdForUpdate(targetTabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (target.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Target tab is not open.");
        }
        if (target.getBillTotal() != null) {
            throw new IllegalStateException("Payment has already started for the target tab.");
        }

        Tab source = tabRepository.findByIdAndRestaurantIdForUpdate(request.getSourceTabId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (source.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Source tab is not open.");
        }
        if (source.getBillTotal() != null) {
            throw new IllegalStateException("Payment has already started for the source tab.");
        }

        List<Order> sourceOrders = orderRepository.findByTabIdAndRestaurantId(source.getId(), restaurantId);
        sourceOrders.forEach(order -> {
            order.setMergedFromTabId(source.getId());
            order.setTab(target);
        });
        orderRepository.saveAll(sourceOrders);

        // Source keeps its own tables (not cleared) so a short-lived "undo merge" can restore them;
        // it's excluded from OPEN listings anyway since its status is MERGED.
        target.getTables().addAll(source.getTables());

        source.setStatus(TabStatus.MERGED);
        source.setClosedAt(OffsetDateTime.now());
        tabRepository.save(source);
        Tab savedTarget = tabRepository.save(target);

        return toResponse(savedTarget);
    }

    @Transactional
    public TabResponse unmergeTab(UUID restaurantId, UUID targetTabId, UUID sourceTabId) {
        // Locked for the same reason as mergeTab: target is about to lose orders it may already have
        // a frozen bill total based on.
        Tab target = tabRepository.findByIdAndRestaurantIdForUpdate(targetTabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (target.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Target tab is not open.");
        }
        if (target.getBillTotal() != null) {
            throw new IllegalStateException("Payment has already started for the target tab.");
        }

        Tab source = tabRepository.findByIdAndRestaurantIdForUpdate(sourceTabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (source.getStatus() != TabStatus.MERGED) {
            throw new IllegalArgumentException("Source tab was not merged into this tab.");
        }

        List<Order> movedOrders = orderRepository.findByTabIdAndMergedFromTabId(targetTabId, sourceTabId);
        movedOrders.forEach(order -> {
            order.setTab(source);
            order.setMergedFromTabId(null);
        });
        orderRepository.saveAll(movedOrders);

        target.getTables().removeAll(source.getTables());
        Tab savedTarget = tabRepository.save(target);

        source.setStatus(TabStatus.OPEN);
        source.setClosedAt(null);
        tabRepository.save(source);

        return toResponse(savedTarget);
    }

    /**
     * Registers one or more payments atomically against a tab, either while it's OPEN (normal
     * collection, possibly split across several calls) or, OWNER/MANAGER only, on a CLOSED tab left
     * with a debt by a prior {@link #voidPayment}. Closes the tab (frees tables) the moment the active
     * payment sum reaches the tab's frozen {@code billTotal} — but only if the tab started this call
     * OPEN; completing a closed-tab correction never re-touches status/closedAt/tables.
     */
    @Transactional
    public TabResponse registerPayments(UUID restaurantId, UUID tabId, UserRole currentUserRole, UUID actingUserId, RegisterPaymentsRequest request) {
        Tab tab = tabRepository.findByIdAndRestaurantIdForUpdate(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));

        BigDecimal alreadyPaid = paymentRepository.sumActiveAmountByTabId(tabId);
        boolean startedOpen = tab.getStatus() == TabStatus.OPEN;
        boolean isClosedTopUp = tab.getStatus() == TabStatus.CLOSED
                && tab.getBillTotal() != null
                && alreadyPaid.compareTo(tab.getBillTotal()) < 0;

        if (!startedOpen && !isClosedTopUp) {
            throw new IllegalArgumentException("Tab is not open.");
        }
        if (isClosedTopUp && currentUserRole != UserRole.OWNER && currentUserRole != UserRole.MANAGER) {
            throw new IllegalStateException("Only OWNER or MANAGER may complete a payment correction on a closed tab.");
        }
        if (startedOpen && orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(tabId, List.of(ItemStatus.DELIVERED, ItemStatus.CANCELLED))) {
            throw new IllegalStateException("Tab has order items that are not DELIVERED or CANCELLED yet.");
        }

        BigDecimal total = resolveBillTotal(tab, currentUserRole, request.getServiceChargePercentage());
        BigDecimal remaining = total.subtract(alreadyPaid);

        OffsetDateTime now = OffsetDateTime.now();
        List<Payment> newPayments = new ArrayList<>();
        BigDecimal sumOfEntries = BigDecimal.ZERO;
        for (PaymentEntryRequest entry : request.getPayments()) {
            if (entry.getAmount().scale() > 2) {
                throw new IllegalArgumentException("Amount must not have more than 2 decimal places.");
            }
            if (entry.getPaymentMethod() == PaymentMethod.CASH) {
                cashRegisterService.requireOpenSession(restaurantId);
            }
            sumOfEntries = sumOfEntries.add(entry.getAmount());
            newPayments.add(Payment.builder()
                    .tab(tab)
                    .restaurant(tab.getRestaurant())
                    .paymentMethod(entry.getPaymentMethod())
                    .amount(entry.getAmount())
                    .status(PaymentStatus.ACTIVE)
                    .paidAt(now)
                    .createdBy(userRepository.getReferenceById(actingUserId))
                    .build());
        }

        if (sumOfEntries.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds the remaining balance of " + remaining);
        }

        paymentRepository.saveAll(newPayments);

        BigDecimal newPaidSum = alreadyPaid.add(sumOfEntries);
        if (startedOpen && newPaidSum.compareTo(total) == 0) {
            tab.setStatus(TabStatus.CLOSED);
            tab.setClosedAt(now);
            tab = tabRepository.save(tab);
            tab.getTables().forEach(table -> table.setStatus(TableStatus.FREE));
            tableRepository.saveAll(tab.getTables());
        } else {
            tab = tabRepository.save(tab);
        }

        return toResponse(tab);
    }

    /**
     * Voids a single payment (OWNER/MANAGER only, enforced at the controller). Never reopens a CLOSED
     * tab and never touches its tables, regardless of the resulting balance — by the time someone
     * reaches this action the table may already be free or serving someone else entirely, and resuming
     * service would be a separate, bigger feature. A debt left on a CLOSED tab is settled later through
     * {@link #registerPayments}.
     */
    @Transactional
    public TabResponse voidPayment(UUID restaurantId, UUID tabId, UUID paymentId, UUID actingUserId, VoidPaymentRequest request) {
        Tab tab = tabRepository.findByIdAndRestaurantIdForUpdate(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));

        Payment payment = paymentRepository.findByIdAndTabIdAndRestaurantId(paymentId, tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found."));
        if (payment.getStatus() != PaymentStatus.ACTIVE) {
            throw new IllegalArgumentException("Payment is already voided.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        payment.setStatus(PaymentStatus.VOIDED);
        payment.setVoidedBy(userRepository.getReferenceById(actingUserId));
        payment.setVoidedAt(now);
        payment.setVoidReason(request.getReason());
        paymentRepository.save(payment);

        if (tab.getStatus() == TabStatus.OPEN && paymentRepository.sumActiveAmountByTabId(tabId).signum() == 0) {
            tab.setBillTotal(null);
            tab.setServiceChargePercentage(null);
            tab.setServiceChargeAmount(null);
            tabRepository.save(tab);
        }

        return toResponse(tab);
    }

    @Transactional
    public TabResponse cancelTab(UUID restaurantId, UUID tabId) {
        Tab tab = findByIdAndRestaurant(restaurantId, tabId);
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }
        if (orderRepository.existsByTabId(tabId)) {
            throw new IllegalStateException("Cannot cancel a tab that already has orders.");
        }

        tab.setStatus(TabStatus.CLOSED);
        tab.setClosedAt(OffsetDateTime.now());
        tab = tabRepository.save(tab);

        tab.getTables().forEach(table -> table.setStatus(TableStatus.FREE));
        tableRepository.saveAll(tab.getTables());

        return toResponse(tab);
    }

    @Transactional
    public TabResponse markReceiptPrinted(UUID restaurantId, UUID tabId) {
        Tab tab = findByIdAndRestaurant(restaurantId, tabId);
        tab.setReceiptPrintedAt(OffsetDateTime.now());
        return toResponse(tabRepository.save(tab));
    }

    @Transactional
    public TabResponse applyDiscount(UUID restaurantId, UUID tabId, String actingUserName, ApplyDiscountRequest request) {
        Tab tab = tabRepository.findByIdAndRestaurantIdForUpdate(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }
        if (tab.getBillTotal() != null) {
            throw new IllegalStateException("Payment has already started for this tab.");
        }

        if (request.getDiscountType() == null) {
            tab.setDiscountType(null);
            tab.setDiscountValue(null);
            tab.setDiscountReason(null);
            tab.setDiscountAppliedBy(null);
            tab.setDiscountAppliedAt(null);
        } else {
            BigDecimal itemsTotal = computeItemsTotal(restaurantId, tabId);
            validateDiscountValue(request.getDiscountType(), request.getDiscountValue(), itemsTotal);
            tab.setDiscountType(request.getDiscountType());
            tab.setDiscountValue(request.getDiscountValue());
            tab.setDiscountReason(request.getReason());
            tab.setDiscountAppliedBy(actingUserName);
            tab.setDiscountAppliedAt(OffsetDateTime.now());
        }

        return toResponse(tabRepository.save(tab));
    }

    // There is no persisted RESERVED table status (the project has no scheduled/cron job to flip it
    // back to FREE later, and every other time-based alert here is computed live rather than stored --
    // see ReservationService). Instead, opening a table is rejected if a SCHEDULED reservation's
    // blocking window (reservation time +/- the restaurant's configured minutes) contains this exact
    // moment. This self-expires with no extra bookkeeping: once "now" moves past a reservation's
    // window, it simply stops matching here.
    private void assertTablesNotReserved(List<RestaurantTable> tables, UUID restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowStart = now.minusMinutes(restaurant.getReservationBlockAfterMinutes());
        OffsetDateTime windowEnd = now.plusMinutes(restaurant.getReservationBlockBeforeMinutes());
        List<UUID> tableIds = tables.stream().map(RestaurantTable::getId).toList();
        if (reservationRepository.existsBlockingReservation(tableIds, windowStart, windowEnd)) {
            throw new IllegalStateException("One or more tables are reserved around this time.");
        }
    }

    /** Returns the tab's frozen bill total, computing and persisting it (along with the resolved
     * service charge) the first time a payment is registered against this tab. Every payment after
     * that compares against this same frozen value, never a freshly recomputed one. */
    private BigDecimal resolveBillTotal(Tab tab, UserRole role, BigDecimal requestedServiceChargePercentage) {
        if (tab.getBillTotal() != null) {
            return tab.getBillTotal();
        }

        BigDecimal itemsTotal = computeItemsTotal(tab.getRestaurant().getId(), tab.getId());
        BigDecimal afterDiscount = itemsTotal.subtract(tab.getDiscountAmount(itemsTotal));

        BigDecimal serviceChargePercentage = resolveServiceChargePercentage(tab.getRestaurant().getId(), role, requestedServiceChargePercentage);
        BigDecimal serviceChargeAmount = computeServiceChargeAmount(afterDiscount, serviceChargePercentage);

        tab.setServiceChargePercentage(serviceChargePercentage);
        tab.setServiceChargeAmount(serviceChargePercentage != null ? serviceChargeAmount : null);
        tab.setBillTotal(afterDiscount.add(serviceChargeAmount));
        return tab.getBillTotal();
    }

    // Only OWNER/MANAGER may override or waive the service charge; other roles always get the
    // restaurant's configured default, so a waiter/cashier can't quietly pocket or inflate it.
    private BigDecimal resolveServiceChargePercentage(UUID restaurantId, UserRole role, BigDecimal requested) {
        if (role == UserRole.OWNER || role == UserRole.MANAGER) {
            return requested;
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));
        return Boolean.TRUE.equals(restaurant.getServiceChargeEnabled())
                ? restaurant.getServiceChargePercentage()
                : null;
    }

    private BigDecimal computeServiceChargeAmount(BigDecimal afterDiscount, BigDecimal serviceChargePercentage) {
        if (serviceChargePercentage == null) {
            return BigDecimal.ZERO;
        }
        if (serviceChargePercentage.signum() < 0 || serviceChargePercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Service charge percentage must be between 0 and 100.");
        }
        return afterDiscount.multiply(serviceChargePercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void validateDiscountValue(DiscountType type, BigDecimal value, BigDecimal baseAmount) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero.");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100.");
        }
        if (type == DiscountType.FIXED && value.compareTo(baseAmount) > 0) {
            throw new IllegalArgumentException("Discount amount cannot exceed the amount being discounted.");
        }
    }

    private BigDecimal computeItemsTotal(UUID restaurantId, UUID tabId) {
        return orderItemRepository
                .findByOrder_Tab_IdAndOrder_Restaurant_IdAndStatusAndParentOrderItemIsNull(tabId, restaurantId, ItemStatus.DELIVERED).stream()
                .map(OrderItem::getNetSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Tab findByIdAndRestaurant(UUID restaurantId, UUID tabId) {
        return tabRepository.findByIdAndRestaurantId(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
    }

    private TabResponse toResponse(Tab tab) {
        List<Payment> payments = paymentRepository.findByTabIdOrderByPaidAtDesc(tab.getId());
        BigDecimal amountPaid = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.ACTIVE)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TabResponse.builder()
                .id(tab.getId())
                .restaurantId(tab.getRestaurant().getId())
                .status(tab.getStatus())
                .openedAt(tab.getOpenedAt())
                .lastOrderAt(tab.getLastOrderAt())
                .closedAt(tab.getClosedAt())
                .billTotal(tab.getBillTotal())
                .amountPaid(amountPaid)
                .remainingBalance(tab.getBillTotal() != null ? tab.getBillTotal().subtract(amountPaid) : null)
                .payments(payments.stream().map(this::toPaymentResponse).toList())
                .receiptPrintedAt(tab.getReceiptPrintedAt())
                .discountType(tab.getDiscountType())
                .discountValue(tab.getDiscountValue())
                .discountReason(tab.getDiscountReason())
                .discountAppliedBy(tab.getDiscountAppliedBy())
                .discountAppliedAt(tab.getDiscountAppliedAt())
                .serviceChargePercentage(tab.getServiceChargePercentage())
                .serviceChargeAmount(tab.getServiceChargeAmount())
                .tables(tab.getTables().stream()
                        .map(table -> TabTableSummary.builder()
                                .id(table.getId())
                                .number(table.getNumber())
                                .build())
                        .toList())
                .build();
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paidAt(payment.getPaidAt())
                .createdByName(payment.getCreatedBy() != null ? payment.getCreatedBy().getName() : null)
                .voidedByName(payment.getVoidedBy() != null ? payment.getVoidedBy().getName() : null)
                .voidedAt(payment.getVoidedAt())
                .voidReason(payment.getVoidReason())
                .build();
    }
}
