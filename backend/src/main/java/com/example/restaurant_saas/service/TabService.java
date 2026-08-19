package com.example.restaurant_saas.service;

import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.entity.Order;
import com.example.restaurant_saas.domain.entity.OrderItem;
import com.example.restaurant_saas.domain.entity.Payment;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.entity.User;
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
import com.example.restaurant_saas.domain.entity.CardCharge;
import com.example.restaurant_saas.domain.enums.CardChargeStatus;
import com.example.restaurant_saas.domain.enums.PixChargeStatus;
import com.example.restaurant_saas.repository.CardChargeRepository;
import com.example.restaurant_saas.repository.DeliveryDetailsRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.OrderRepository;
import com.example.restaurant_saas.repository.PaymentRepository;
import com.example.restaurant_saas.repository.PixChargeRepository;
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
    private final PixChargeRepository pixChargeRepository;
    private final CardChargeRepository cardChargeRepository;
    private final DeliveryDetailsRepository deliveryDetailsRepository;

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

        // Locked (not a plain read): two concurrent openTab calls for the same table must not
        // both pass the FREE check below before either commits, which would open two tabs on one
        // table - mirrors the same locked-read pattern already used by openOrGetTabForTable,
        // addTable and mergeTab.
        List<RestaurantTable> tables = tableRepository.findByIdInAndRestaurantIdForUpdate(distinctIds, restaurantId);
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
        return registerPayments(restaurantId, tabId, currentUserRole, actingUserId, request, null);
    }

    /**
     * Same as the 4-args-plus-request overload, but lets an internal caller
     * ({@link CardChargeService}'s webhook handler) attach the originating {@link CardCharge} to
     * every {@link Payment} this call creates - never exposed through the public
     * {@link RegisterPaymentsRequest} DTO, so staff can never claim a manual entry is a
     * gateway-confirmed card payment. {@code cardChargeId} is null for every other caller (staff
     * registering cash/Pix/manual card entries).
     */
    @Transactional
    public TabResponse registerPayments(
            UUID restaurantId, UUID tabId, UserRole currentUserRole, UUID actingUserId, RegisterPaymentsRequest request,
            UUID cardChargeId
    ) {
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
        // Same delivery exemption as freezeBillTotalForPixCharge - a delivery order has no "food
        // reached the table" step to gate on, and staff may need to register its payment manually
        // (a failed/missed webhook, or the customer paying by another means before the Pix/card
        // charge confirms).
        boolean isDeliveryOrder = deliveryDetailsRepository.findByTab_Id(tabId).isPresent();
        if (startedOpen && !isDeliveryOrder && orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(tabId, List.of(ItemStatus.DELIVERED, ItemStatus.CANCELLED))) {
            throw new IllegalStateException("Tab has order items that are not DELIVERED or CANCELLED yet.");
        }

        // serviceChargePercentage itself has to actually be present to count as an override - a
        // caller (e.g. an OWNER splitting the bill across several manual entries, none of which
        // has an opinion on service charge) that merely didn't address it must still fall back to
        // the restaurant's default, not silently waive it to zero. Same reasoning, and same fix,
        // as TabController#createPixCharge's allowServiceChargeOverride gating.
        boolean allowServiceChargeOverride = request.getServiceChargePercentage() != null
                && (currentUserRole == UserRole.OWNER || currentUserRole == UserRole.MANAGER);
        BigDecimal total = resolveBillTotal(tab, allowServiceChargeOverride, request.getServiceChargePercentage());
        // A sibling PENDING Pix or card charge (a split-comanda QR/checkout link someone else is
        // still paying) has already claimed part of this total even though nothing's been paid for
        // it yet - without this, a manual submit could close the tab "early" while that charge is
        // still outstanding, and its webhook would then have nowhere valid to register the real
        // money that lands for it. (The charge THIS call is itself confirming, if any, is already
        // PAID by this point - see PixChargeService#handleWebhook/CardChargeService#handleWebhook -
        // so it's excluded from this sum on its own.)
        BigDecimal pendingGatewayTotal = pixChargeRepository.sumAmountByTabIdAndStatus(tabId, PixChargeStatus.PENDING)
                .add(cardChargeRepository.sumAmountByTabIdAndStatus(tabId, CardChargeStatus.PENDING));
        BigDecimal remaining = total.subtract(alreadyPaid).subtract(pendingGatewayTotal);

        OffsetDateTime now = OffsetDateTime.now();
        // actingUserId is null when this is a system-initiated payment (a Pix charge confirmed by
        // Woovi's webhook, see PixChargeService) rather than staff registering it - same nullable
        // createdBy already used for self-service orders (Order.createdBy).
        User actingUser = actingUserId != null ? userRepository.getReferenceById(actingUserId) : null;
        CardCharge cardCharge = cardChargeId != null ? cardChargeRepository.getReferenceById(cardChargeId) : null;
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
                    .createdBy(actingUser)
                    .cardCharge(cardCharge)
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

        // A still-PENDING Pix or card charge (a split-comanda QR/checkout link nobody's paid yet,
        // or another payer's half) is a reason not to unfreeze even if every real payment on the
        // tab was just voided away - that charge still shows a specific amount computed against
        // the frozen total.
        if (tab.getStatus() == TabStatus.OPEN && paymentRepository.sumActiveAmountByTabId(tabId).signum() == 0
                && hasNoOutstandingChargeCommitments(tabId)) {
            tab.setBillTotal(null);
            tab.setServiceChargePercentage(null);
            tab.setServiceChargeAmount(null);
            tabRepository.save(tab);
        }

        return toResponse(tab);
    }

    private boolean hasNoOutstandingChargeCommitments(UUID tabId) {
        return pixChargeRepository.sumAmountByTabIdAndStatus(tabId, PixChargeStatus.PENDING).signum() == 0
                && cardChargeRepository.sumAmountByTabIdAndStatus(tabId, CardChargeStatus.PENDING).signum() == 0;
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
    private BigDecimal resolveBillTotal(Tab tab, boolean allowServiceChargeOverride, BigDecimal requestedServiceChargePercentage) {
        if (tab.getBillTotal() != null) {
            return tab.getBillTotal();
        }

        BigDecimal itemsTotal = computeItemsTotal(tab.getRestaurant().getId(), tab.getId());
        BigDecimal afterDiscount = itemsTotal.subtract(tab.getDiscountAmount(itemsTotal));

        Optional<DeliveryDetails> deliveryDetails = deliveryDetailsRepository.findByTab_Id(tab.getId());

        // No service charge on delivery orders (2026-08-18 decision) - it exists to compensate
        // table service, which doesn't happen here, and the delivery fee already covers the cost
        // of getting the order to the customer. Charging both read as a hidden second fee (the
        // total didn't match items + delivery fee, with nothing explaining the gap). Unconditional -
        // not gated by allowServiceChargeOverride/the restaurant's own toggle, since this isn't a
        // waivable adjustment, it's "the charge doesn't apply to this kind of order" at all.
        BigDecimal serviceChargePercentage = deliveryDetails.isPresent()
                ? null
                : resolveServiceChargePercentage(tab.getRestaurant().getId(), allowServiceChargeOverride, requestedServiceChargePercentage);
        BigDecimal serviceChargeAmount = computeServiceChargeAmount(afterDiscount, serviceChargePercentage);

        // Already frozen at order creation time (PublicDeliveryOrderService) from the DeliveryZone
        // matched then - zero for every tab that isn't a delivery order (no DeliveryDetails row).
        BigDecimal deliveryFee = deliveryDetails.map(DeliveryDetails::getDeliveryFee).orElse(BigDecimal.ZERO);

        tab.setServiceChargePercentage(serviceChargePercentage);
        tab.setServiceChargeAmount(serviceChargePercentage != null ? serviceChargeAmount : null);
        tab.setBillTotal(afterDiscount.add(serviceChargeAmount).add(deliveryFee));
        return tab.getBillTotal();
    }

    // Only OWNER/MANAGER may override or waive the service charge; every other caller - other
    // staff roles, and a Pix charge started from the digital menu, which has no staff role at all
    // - always gets the restaurant's configured default, so nobody can quietly pocket or inflate it.
    // requested being null here deliberately still means "waive it" when allowOverride is true (an
    // OWNER/MANAGER removing the charge) - callers that merely didn't address service charge at all
    // (e.g. creating a Pix charge with no request body) are expected to pass allowOverride=false
    // instead of an empty override, so that omission reads as "default", not "waived".
    private BigDecimal resolveServiceChargePercentage(UUID restaurantId, boolean allowOverride, BigDecimal requested) {
        if (allowOverride) {
            return requested;
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found."));
        return Boolean.TRUE.equals(restaurant.getServiceChargeEnabled())
                ? restaurant.getServiceChargePercentage()
                : null;
    }

    /**
     * Freezes and returns the tab's bill total when a Pix charge is created for it - the same
     * freeze {@link #registerPayments} does on a tab's first real payment, just triggered earlier
     * (the moment a QR code is generated, not the moment it's paid). This keeps the amount on a
     * QR code the customer is looking at from silently drifting if someone edits items/discount
     * while it's outstanding - applyDiscount/mergeTab/unmergeTab already refuse once billTotal is
     * set. {@code allowServiceChargeOverride} mirrors registerPayments: true only when an
     * authenticated OWNER/MANAGER generated this charge from the Caixa (PixChargeService#createCharge,
     * called from TabController), false for the customer self-service path from the digital menu
     * (PixChargeService#createChargeForTable), where there's no staff role behind the call at all
     * and the restaurant's configured default is the only sane choice.
     */
    @Transactional
    public BigDecimal freezeBillTotalForPixCharge(
            UUID restaurantId, UUID tabId, boolean allowServiceChargeOverride, BigDecimal requestedServiceChargePercentage
    ) {
        Tab tab = tabRepository.findByIdAndRestaurantIdForUpdate(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (tab.getStatus() != TabStatus.OPEN) {
            throw new IllegalArgumentException("Tab is not open.");
        }
        // Delivery orders (task 29.1) skip the "must be DELIVERED first" gate: that rule exists so a
        // dine-in tab's total can't be frozen for payment while items might still be added, using
        // "food reached the table" as the signal the order is final. A delivery order has no such
        // step - it's final the moment it's placed, and payment happens immediately, before the
        // kitchen even starts (items are still PENDING) - see docs/DELIVERY.md.
        boolean isDeliveryOrder = deliveryDetailsRepository.findByTab_Id(tabId).isPresent();
        if (!isDeliveryOrder && orderItemRepository.existsByOrder_Tab_IdAndStatusNotIn(tabId, List.of(ItemStatus.DELIVERED, ItemStatus.CANCELLED))) {
            throw new IllegalStateException("Tab has order items that are not DELIVERED or CANCELLED yet.");
        }

        BigDecimal total = resolveBillTotal(tab, allowServiceChargeOverride, requestedServiceChargePercentage);
        tabRepository.save(tab);
        return total;
    }

    /**
     * Reverses {@link #freezeBillTotalForPixCharge} when the charge(s) it was frozen for are being
     * cancelled unpaid (PixChargeService/CardChargeService#cancelPendingCharge, #cancelCharge) -
     * same unfreeze {@link #voidPayment} does after reversing a payment, just triggered from the
     * other side (nobody ever paid, rather than a paid amount being reversed). Silently does
     * nothing instead of throwing when it's not actually safe yet - a real payment already landed
     * on the tab (that payment was computed against the frozen total; unfreezing under it would
     * leave items/discount/service-charge editable again while the payment on record still
     * reflects the old, now-stale total), or a sibling Pix/card charge is still PENDING and needs
     * the freeze to stay put - since cancelling one charge isn't a request to unfreeze at all
     * costs, only if that charge was the last thing still relying on the freeze.
     *
     * <p>Used to throw instead of no-op on an already-landed payment - found the hard way
     * (2026-08-16) when cancelling a card charge on a split-comanda tab already partially paid via
     * Pix threw an unhandled 500 straight to the Caixa UI instead of just cancelling the charge and
     * correctly leaving the total frozen.
     */
    @Transactional
    public void unfreezeBillTotalIfNoCommitments(UUID restaurantId, UUID tabId) {
        Tab tab = tabRepository.findByIdAndRestaurantIdForUpdate(tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Tab not found."));
        if (tab.getStatus() != TabStatus.OPEN) {
            return;
        }
        if (paymentRepository.sumActiveAmountByTabId(tabId).signum() != 0) {
            return;
        }
        if (!hasNoOutstandingChargeCommitments(tabId)) {
            return;
        }

        tab.setBillTotal(null);
        tab.setServiceChargePercentage(null);
        tab.setServiceChargeAmount(null);
        tabRepository.save(tab);
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

    // Dine-in only counts DELIVERED items - the bill reflects what actually reached the table, and
    // the earlier gate (must be all DELIVERED/CANCELLED) keeps anything else from being paid for
    // at all. A delivery order has no per-item "delivered to table" step: it's paid immediately at
    // submission (items still PENDING), so every non-cancelled item counts instead.
    private BigDecimal computeItemsTotal(UUID restaurantId, UUID tabId) {
        boolean isDeliveryOrder = deliveryDetailsRepository.findByTab_Id(tabId).isPresent();
        List<OrderItem> items = isDeliveryOrder
                ? orderItemRepository.findByOrder_Tab_IdAndOrder_Restaurant_IdAndStatusNotAndParentOrderItemIsNull(tabId, restaurantId, ItemStatus.CANCELLED)
                : orderItemRepository.findByOrder_Tab_IdAndOrder_Restaurant_IdAndStatusAndParentOrderItemIsNull(tabId, restaurantId, ItemStatus.DELIVERED);
        return items.stream().map(OrderItem::getNetSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
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
        Optional<DeliveryDetails> deliveryDetails = deliveryDetailsRepository.findByTab_Id(tab.getId());

        return TabResponse.builder()
                .id(tab.getId())
                .restaurantId(tab.getRestaurant().getId())
                .status(tab.getStatus())
                .deliveryStatus(deliveryDetails.map(DeliveryDetails::getStatus).orElse(null))
                .deliveryFee(deliveryDetails.map(DeliveryDetails::getDeliveryFee).orElse(null))
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
