package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.CardCharge;
import com.example.restaurant_saas.domain.entity.CardIntegration;
import com.example.restaurant_saas.domain.entity.DeliveryDetails;
import com.example.restaurant_saas.domain.entity.Payment;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.CardChargeStatus;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.PaymentStatus;
import com.example.restaurant_saas.domain.enums.PixChargeStatus;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.request.VoidPaymentRequest;
import com.example.restaurant_saas.dto.response.CardChargeResponse;
import com.example.restaurant_saas.dto.response.CardIntegrationStatusResponse;
import com.example.restaurant_saas.dto.response.TabResponse;
import com.example.restaurant_saas.exception.CardGatewayException;
import com.example.restaurant_saas.repository.CardChargeRepository;
import com.example.restaurant_saas.repository.CardIntegrationRepository;
import com.example.restaurant_saas.repository.DeliveryDetailsRepository;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.PaymentRepository;
import com.example.restaurant_saas.repository.PixChargeRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import com.example.restaurant_saas.repository.TabRepository;
import com.example.restaurant_saas.security.CredentialEncryptionService;
import com.example.restaurant_saas.security.MercadoPagoWebhookSignatureVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardChargeService {

    private static final String PAYMENT_APPROVED_STATUS = "approved";
    private static final String PAYMENT_REJECTED_STATUS = "rejected";

    private final CardIntegrationRepository cardIntegrationRepository;
    private final CardChargeRepository cardChargeRepository;
    private final PixChargeRepository pixChargeRepository;
    private final PaymentRepository paymentRepository;
    private final TabRepository tabRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final OrderItemRepository orderItemRepository;
    private final DeliveryDetailsRepository deliveryDetailsRepository;
    private final CredentialEncryptionService credentialEncryptionService;
    private final MercadoPagoApiClient mercadoPagoApiClient;
    private final MercadoPagoWebhookSignatureVerifier signatureVerifier;
    private final TabService tabService;
    private final TenantActivator tenantActivator;
    private final ObjectMapper objectMapper;

    @Value("${app.backend-url}")
    private String backendUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Each field is independently optional: a blank one means "leave that credential as it
     * already is", so rotating just the access token (or just the webhook secret) doesn't force
     * re-pasting the other - the status endpoint never returns the stored values back, so there
     * would be no way to know what to re-paste anyway. Both are required together the first time
     * a restaurant connects, since there's nothing yet to leave unchanged.
     */
    @Transactional
    public void saveIntegration(UUID restaurantId, String rawAccessToken, String rawWebhookSecret) {
        Optional<CardIntegration> existing = cardIntegrationRepository.findByRestaurantId(restaurantId);
        boolean hasAccessToken = rawAccessToken != null && !rawAccessToken.isBlank();
        boolean hasWebhookSecret = rawWebhookSecret != null && !rawWebhookSecret.isBlank();

        if (existing.isEmpty() && (!hasAccessToken || !hasWebhookSecret)) {
            throw new IllegalArgumentException("Both the access token and webhook secret are required to connect for the first time.");
        }
        if (!hasAccessToken && !hasWebhookSecret) {
            throw new IllegalArgumentException("Nothing to update - fill in the access token, the webhook secret, or both.");
        }

        CardIntegration integration = existing.orElseGet(() -> CardIntegration.builder().restaurantId(restaurantId).build());
        if (hasAccessToken) {
            integration.setAccessTokenEncrypted(credentialEncryptionService.encrypt(rawAccessToken));
        }
        if (hasWebhookSecret) {
            integration.setWebhookSecretEncrypted(credentialEncryptionService.encrypt(rawWebhookSecret));
        }
        cardIntegrationRepository.save(integration);
    }

    @Transactional(readOnly = true)
    public CardIntegrationStatusResponse getIntegrationStatus(UUID restaurantId) {
        boolean configured = cardIntegrationRepository.findByRestaurantId(restaurantId)
                .filter(i -> i.getAccessTokenEncrypted() != null && i.getWebhookSecretEncrypted() != null)
                .isPresent();
        return CardIntegrationStatusResponse.builder().configured(configured).build();
    }

    /**
     * Creates a card charge for a tab: freezes the tab's bill total (see
     * {@link TabService#freezeBillTotalForPixCharge} - not Pix-specific despite the name, shared by
     * both gateway integrations), asks Mercado Pago for a Checkout Pro preference, and records a
     * PENDING {@link CardCharge} so the webhook can later find its way back to this tenant.
     * {@code requestedAmount} is null for the common case (one charge covering the tab's entire
     * remaining balance); a caller splitting the bill passes an explicit partial amount instead,
     * one call per checkout link - always validated against what's actually still uncommitted (not
     * already paid, and not already claimed by another still-PENDING Pix *or* card charge on the
     * same tab) before Mercado Pago is ever called.
     */
    @Transactional
    public CardChargeResponse createCharge(
            UUID restaurantId, UUID tabId, boolean allowServiceChargeOverride, BigDecimal requestedServiceChargePercentage,
            BigDecimal requestedAmount
    ) {
        CardIntegration integration = cardIntegrationRepository.findByRestaurantId(restaurantId)
                .filter(i -> i.getAccessTokenEncrypted() != null && i.getWebhookSecretEncrypted() != null)
                .orElseThrow(() -> new IllegalStateException("This restaurant hasn't configured card payment yet."));

        BigDecimal billTotal = tabService.freezeBillTotalForPixCharge(
                restaurantId, tabId, allowServiceChargeOverride, requestedServiceChargePercentage);
        Tab tab = tabRepository.getReferenceById(tabId);

        BigDecimal amount = resolveAndValidateAmount(tabId, billTotal, requestedAmount);

        String accessToken = credentialEncryptionService.decrypt(integration.getAccessTokenEncrypted());
        String externalReference = UUID.randomUUID().toString();

        MercadoPagoApiClient.PreferenceResult result = mercadoPagoApiClient.createPreference(
                accessToken, externalReference, amount, "Comanda " + tabId,
                buildNotificationUrl(restaurantId),
                buildStaffReturnUrl("success", externalReference),
                buildStaffReturnUrl("pending", externalReference),
                buildStaffReturnUrl("failure", externalReference));

        CardCharge charge = CardCharge.builder()
                .restaurantId(restaurantId)
                .tab(tab)
                .externalReference(externalReference)
                .amount(amount)
                .status(CardChargeStatus.PENDING)
                .initPointUrl(result.initPoint())
                .build();
        charge = cardChargeRepository.save(charge);

        return toResponse(charge);
    }

    /**
     * Same as {@link #createCharge}, but for a customer paying on their own from the digital menu
     * instead of staff generating the checkout link from the Caixa - resolves the tab from the
     * restaurant's slug and table id (same public lookup as
     * {@link com.example.restaurant_saas.service.PublicTableRequestService}) instead of trusting
     * an authenticated staff session's restaurantId. Gated on the tab having at least one DELIVERED
     * item, same as the "Pedir a conta" request and {@link PixChargeService#createChargeForTable}.
     */
    @Transactional
    public CardChargeResponse createChargeForTable(String slug, UUID tableId) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Menu not found."));

        tenantActivator.activate(restaurant.getId());
        try {
            RestaurantTable table = restaurantTableRepository.findByIdAndRestaurantId(tableId, restaurant.getId())
                    .filter(t -> Boolean.TRUE.equals(t.getActive()))
                    .orElseThrow(() -> new IllegalArgumentException("Table not found."));

            Tab tab = tabRepository.findOpenTabByRestaurantIdAndTableId(restaurant.getId(), table.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Table has no open tab."));

            boolean hasDeliveredItems = orderItemRepository.existsByOrder_Tab_IdAndStatus(tab.getId(), ItemStatus.DELIVERED);
            if (!hasDeliveredItems) {
                throw new IllegalArgumentException("Cannot start a card charge for a table with no delivered items yet.");
            }

            CardIntegration integration = cardIntegrationRepository.findByRestaurantId(restaurant.getId())
                    .filter(i -> i.getAccessTokenEncrypted() != null && i.getWebhookSecretEncrypted() != null)
                    .orElseThrow(() -> new IllegalStateException("This restaurant hasn't configured card payment yet."));

            BigDecimal billTotal = tabService.freezeBillTotalForPixCharge(restaurant.getId(), tab.getId(), false, null);
            BigDecimal amount = resolveAndValidateAmount(tab.getId(), billTotal, null);

            String accessToken = credentialEncryptionService.decrypt(integration.getAccessTokenEncrypted());
            String externalReference = UUID.randomUUID().toString();

            MercadoPagoApiClient.PreferenceResult result = mercadoPagoApiClient.createPreference(
                    accessToken, externalReference, amount, "Comanda " + tab.getId(),
                    buildNotificationUrl(restaurant.getId()),
                    buildMenuReturnUrl(slug, tableId, "success", externalReference),
                    buildMenuReturnUrl(slug, tableId, "pending", externalReference),
                    buildMenuReturnUrl(slug, tableId, "failure", externalReference));

            CardCharge charge = CardCharge.builder()
                    .restaurantId(restaurant.getId())
                    .tab(tab)
                    .externalReference(externalReference)
                    .amount(amount)
                    .status(CardChargeStatus.PENDING)
                    .initPointUrl(result.initPoint())
                    .build();
            charge = cardChargeRepository.save(charge);

            return toResponse(charge);
        } finally {
            tenantActivator.deactivate();
        }
    }

    /**
     * Same as {@link #createChargeForTable}, but for a delivery order (task 29.1): resolved by the
     * customer's own access token instead of slug+tableId (there's no table), and with no
     * DELIVERED-item gate - a delivery order is paid immediately at submission, before the kitchen
     * even starts. {@link TabService#freezeBillTotalForPixCharge} already knows to skip that gate
     * for delivery tabs. Redirects back to the delivery status page instead of a table's menu URL.
     */
    @Transactional
    public CardChargeResponse createChargeForDeliveryOrder(String accessToken) {
        DeliveryDetails deliveryDetails = deliveryDetailsRepository.findByAccessTokenBypassingRls(accessToken)
                .orElseThrow(() -> new IllegalArgumentException("Delivery order not found."));

        tenantActivator.activate(deliveryDetails.getRestaurantId());
        try {
            UUID restaurantId = deliveryDetails.getRestaurantId();
            UUID tabId = deliveryDetails.getTab().getId();

            CardIntegration integration = cardIntegrationRepository.findByRestaurantId(restaurantId)
                    .filter(i -> i.getAccessTokenEncrypted() != null && i.getWebhookSecretEncrypted() != null)
                    .orElseThrow(() -> new IllegalStateException("This restaurant hasn't configured card payment yet."));

            BigDecimal billTotal = tabService.freezeBillTotalForPixCharge(restaurantId, tabId, false, null);
            BigDecimal amount = resolveAndValidateAmount(tabId, billTotal, null);

            String mpAccessToken = credentialEncryptionService.decrypt(integration.getAccessTokenEncrypted());
            String externalReference = UUID.randomUUID().toString();

            MercadoPagoApiClient.PreferenceResult result = mercadoPagoApiClient.createPreference(
                    mpAccessToken, externalReference, amount, "Comanda " + tabId,
                    buildNotificationUrl(restaurantId),
                    buildDeliveryReturnUrl(accessToken, "success", externalReference),
                    buildDeliveryReturnUrl(accessToken, "pending", externalReference),
                    buildDeliveryReturnUrl(accessToken, "failure", externalReference));

            CardCharge charge = CardCharge.builder()
                    .restaurantId(restaurantId)
                    .tab(tabRepository.getReferenceById(tabId))
                    .externalReference(externalReference)
                    .amount(amount)
                    .status(CardChargeStatus.PENDING)
                    .initPointUrl(result.initPoint())
                    .build();
            charge = cardChargeRepository.save(charge);

            return toResponse(charge);
        } finally {
            tenantActivator.deactivate();
        }
    }

    /** Same as {@link #cancelPendingCharge}, but for a customer backing out of a delivery order's
     * own pending card charge (task 29.1) - e.g. they want to switch to Pix instead, or the
     * checkout link got stuck. Identified by access token, same as {@link #createChargeForDeliveryOrder}. */
    @Transactional
    public void cancelPendingChargeForDeliveryOrder(String accessToken) {
        DeliveryDetails deliveryDetails = deliveryDetailsRepository.findByAccessTokenBypassingRls(accessToken)
                .orElseThrow(() -> new IllegalArgumentException("Delivery order not found."));
        tenantActivator.activate(deliveryDetails.getRestaurantId());
        try {
            cancelPendingCharge(deliveryDetails.getRestaurantId(), deliveryDetails.getTab().getId());
        } finally {
            tenantActivator.deactivate();
        }
    }

    private BigDecimal resolveAndValidateAmount(UUID tabId, BigDecimal billTotal, BigDecimal requestedAmount) {
        if (requestedAmount != null && requestedAmount.scale() > 2) {
            throw new IllegalArgumentException("Amount must not have more than 2 decimal places.");
        }

        // Committed = already-active payments, plus whatever's already claimed by another
        // still-PENDING Pix or card charge on the same tab - a real payment can never succeed on
        // Mercado Pago's side for more than what's actually still owed.
        BigDecimal committed = paymentRepository.sumActiveAmountByTabId(tabId)
                .add(cardChargeRepository.sumAmountByTabIdAndStatus(tabId, CardChargeStatus.PENDING))
                .add(pixChargeRepository.sumAmountByTabIdAndStatus(tabId, PixChargeStatus.PENDING));
        BigDecimal remaining = billTotal.subtract(committed);
        BigDecimal amount = requestedAmount != null ? requestedAmount : remaining;
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Requested amount exceeds the tab's remaining uncommitted balance of " + remaining);
        }
        return amount;
    }

    /**
     * Cancels any still-PENDING card charge(s) on this tab and, if nothing else is still relying
     * on the frozen total (no active payment landed yet, no sibling PENDING Pix/card charge),
     * unfreezes it ({@link TabService#unfreezeBillTotalIfNoCommitments}) so items/discount/service-
     * charge become editable again - for when a checkout link goes unpaid (customer changed their
     * mind, staff generated one by mistake) and the tab needs to keep being worked on. A no-op if
     * there's nothing PENDING. Mirrors {@link PixChargeService#cancelPendingCharge} exactly.
     *
     * <p>Bug fixed 2026-08-16: this used to call the unconditional {@link TabService#unfreezeBillTotal}
     * first, which throws once a real payment already landed on the tab (e.g. a split-comanda tab
     * partially paid via Pix, with this card charge covering the remainder) - cancelling that
     * charge threw an unhandled 500 straight to the Caixa UI instead of just cancelling the charge
     * and correctly leaving the total frozen (a real payment is still counting on it). Charges are
     * now marked CANCELLED and saved *before* the unfreeze check runs, so it sees their true,
     * post-cancellation PENDING sum instead of stale data.
     */
    @Transactional
    public void cancelPendingCharge(UUID restaurantId, UUID tabId) {
        List<CardCharge> pending = cardChargeRepository.findByTab_IdAndStatus(tabId, CardChargeStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        pending.forEach(charge -> charge.setStatus(CardChargeStatus.CANCELLED));
        cardChargeRepository.saveAll(pending);

        tabService.unfreezeBillTotalIfNoCommitments(restaurantId, tabId);
    }

    /** Mirrors {@link PixChargeService#listCharges}: PENDING+PAID+DECLINED (CANCELLED/EXPIRED rows are dead). */
    @Transactional(readOnly = true)
    public List<CardChargeResponse> listCharges(UUID restaurantId, UUID tabId) {
        return cardChargeRepository
                .findByTab_IdAndRestaurantIdAndStatusIn(
                        tabId, restaurantId, List.of(CardChargeStatus.PENDING, CardChargeStatus.PAID, CardChargeStatus.DECLINED))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Mirrors {@link PixChargeService#cancelCharge}. */
    @Transactional
    public void cancelCharge(UUID restaurantId, UUID tabId, UUID chargeId) {
        CardCharge charge = cardChargeRepository.findByIdAndTab_IdAndRestaurantId(chargeId, tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Card charge not found."));
        if (charge.getStatus() != CardChargeStatus.PENDING) {
            return;
        }

        charge.setStatus(CardChargeStatus.CANCELLED);
        cardChargeRepository.save(charge);

        tabService.unfreezeBillTotalIfNoCommitments(restaurantId, tabId);
    }

    /**
     * Handles a Mercado Pago webhook call. {@code restaurantId} comes from the
     * {restaurantId} path segment on the webhook URL itself (each preference is created with a
     * notification_url pointing here) - Mercado Pago's notification body carries no tenant info at
     * all, only a payment id, so the URL is what resolves it, before a token to call Mercado Pago
     * back with is even known.
     *
     * <p><b>Security invariant:</b> the webhook body's own content is never trusted for anything
     * beyond extracting the payment id to look up. The signature is verified against this
     * restaurant's own secret, and then - stronger than the Pix integration, which trusts its
     * webhook body once signature-verified - the payment's real status is independently re-fetched
     * from Mercado Pago's API ({@link MercadoPagoApiClient#getPayment}) before anything is acted
     * on. Mercado Pago's own guidance is to never trust notification bodies for this reason.
     */
    @Transactional
    public void handleWebhook(UUID restaurantId, byte[] rawBody, String signatureHeader, String requestIdHeader) {
        tenantActivator.activate(restaurantId);
        try {
            CardIntegration integration = cardIntegrationRepository.findByRestaurantId(restaurantId).orElse(null);
            if (integration == null || integration.getWebhookSecretEncrypted() == null) {
                return;
            }

            String paymentId = extractPaymentId(rawBody);
            if (paymentId == null) {
                return;
            }

            String webhookSecret = credentialEncryptionService.decrypt(integration.getWebhookSecretEncrypted());
            if (!signatureVerifier.isValid(signatureHeader, requestIdHeader, paymentId, webhookSecret)) {
                throw new IllegalArgumentException("Invalid webhook signature.");
            }

            String accessToken = credentialEncryptionService.decrypt(integration.getAccessTokenEncrypted());
            MercadoPagoApiClient.PaymentResult result = mercadoPagoApiClient.getPayment(accessToken, paymentId);
            if (result.externalReference() == null) {
                return;
            }

            // Bypasses RLS on purpose - same reasoning as PixChargeRepository's equivalent lookup.
            // Explicitly re-checked against the URL's restaurantId right after, as defense in depth
            // beyond the tenant context already active above.
            CardCharge charge = cardChargeRepository.findByExternalReferenceBypassingRls(result.externalReference()).orElse(null);
            if (charge == null || !charge.getRestaurantId().equals(restaurantId) || charge.getStatus() != CardChargeStatus.PENDING) {
                return;
            }

            applyResolvedPayment(charge, paymentId, result);
        } finally {
            tenantActivator.deactivate();
        }
    }

    /**
     * Second path to the exact same authoritative source the webhook uses, triggered instead by
     * the customer's own browser landing on our {@code /pagamento/retorno} redirect right after
     * Checkout Pro - {@code externalReference} comes from a {@code ref} query param we ourselves
     * embedded in the {@code back_urls} when the charge was created (see
     * {@link #buildStaffReturnUrl}/{@link #buildMenuReturnUrl}), never from anything Mercado Pago
     * appends to that URL.
     *
     * <p><b>Why this doesn't weaken the security invariant</b> documented on {@link #handleWebhook}:
     * this never trusts the redirect's own claim of success/failure (that's cosmetic-only, see
     * {@code CardPaymentReturnPage}'s own invariant) - it only uses {@code externalReference} as a
     * lookup key to ask Mercado Pago directly, via {@link MercadoPagoApiClient#searchPaymentByExternalReference},
     * scoped to this restaurant's own access token. An attacker guessing/forging this call gets
     * nothing: Mercado Pago only returns payments that actually belong to the target restaurant's
     * own account, and this method only acts if the result's own external_reference still matches
     * a real PENDING charge - the same two checks {@link #handleWebhook} already relies on instead
     * of the signature. A no-op (never throws) for any charge that's missing, already resolved, or
     * not yet paid on Mercado Pago's side - safe to call as many times as the browser retries.
     *
     * <p>REQUIRES_NEW so this is safe to call from inside a read-only transaction (e.g.
     * DeliveryService#getByAccessToken's polling-triggered check) - a nested REQUIRED transaction
     * would inherit that read-only flag and fail the moment applyResolvedPayment tries to write.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verifyPendingChargeByExternalReference(String externalReference) {
        CardCharge charge = cardChargeRepository.findByExternalReferenceBypassingRls(externalReference).orElse(null);
        if (charge == null || charge.getStatus() != CardChargeStatus.PENDING) {
            return;
        }

        tenantActivator.activate(charge.getRestaurantId());
        try {
            CardIntegration integration = cardIntegrationRepository.findByRestaurantId(charge.getRestaurantId()).orElse(null);
            if (integration == null || integration.getAccessTokenEncrypted() == null) {
                return;
            }

            String accessToken = credentialEncryptionService.decrypt(integration.getAccessTokenEncrypted());
            MercadoPagoApiClient.PaymentResult result = mercadoPagoApiClient.searchPaymentByExternalReference(accessToken, externalReference);
            if (result == null || result.id() == null || !externalReference.equals(result.externalReference())) {
                return;
            }

            applyResolvedPayment(charge, result.id(), result);
        } finally {
            tenantActivator.deactivate();
        }
    }

    /**
     * Shared by {@link #handleWebhook} and {@link #verifyPendingChargeByExternalReference} - both
     * arrive here only after independently confirming {@code result} really is Mercado Pago's own
     * authoritative view of a specific PENDING {@code charge}, just reached via two different
     * triggers (an async webhook vs. the paying browser's own redirect).
     */
    private void applyResolvedPayment(CardCharge charge, String paymentId, MercadoPagoApiClient.PaymentResult result) {
        charge.setMpPaymentId(paymentId);
        charge.setStatusDetail(result.statusDetail());

        if (PAYMENT_APPROVED_STATUS.equals(result.status())) {
            charge.setStatus(CardChargeStatus.PAID);
            cardChargeRepository.save(charge);

            RegisterPaymentsRequest request = new RegisterPaymentsRequest();
            PaymentEntryRequest entry = new PaymentEntryRequest();
            entry.setPaymentMethod(mapPaymentMethod(result.paymentTypeId()));
            entry.setAmount(charge.getAmount());
            request.setPayments(List.of(entry));

            // No staff role/user behind this payment - it was confirmed by Mercado Pago, not
            // registered by someone logged in. Same nullable actingUserId/role pattern as
            // PixChargeService#handleWebhook.
            tabService.registerPayments(charge.getRestaurantId(), charge.getTab().getId(), null, null, request, charge.getId());
        } else if (PAYMENT_REJECTED_STATUS.equals(result.status())) {
            charge.setStatus(CardChargeStatus.DECLINED);
            cardChargeRepository.save(charge);
            tabService.unfreezeBillTotalIfNoCommitments(charge.getRestaurantId(), charge.getTab().getId());
        } else {
            // in_process/pending/etc - not resolved yet, no tab mutation. Another webhook or
            // another verify call will find it resolved later.
            cardChargeRepository.save(charge);
        }
    }

    /**
     * Routes a void request: a manual/cash/Pix payment is voided exactly as before
     * ({@link TabService#voidPayment}), but a payment linked to a PAID {@link CardCharge} first
     * triggers a real gateway refund (V1 scope: full refund only, no partial). Ordering matters for
     * security - the gateway refund must succeed before the internal ledger is mutated, so a failed
     * refund call never leaves a payment marked VOIDED while the money never actually moved back.
     * Kept as a single entry point (staff only ever sees one "estornar" action) rather than a
     * second button they'd need to learn to use for card payments specifically.
     */
    @Transactional
    public TabResponse voidPayment(UUID restaurantId, UUID tabId, UUID paymentId, UUID actingUserId, VoidPaymentRequest request) {
        Payment payment = paymentRepository.findByIdAndTabIdAndRestaurantId(paymentId, tabId, restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found."));

        CardCharge cardCharge = payment.getCardCharge();
        if (cardCharge == null) {
            return tabService.voidPayment(restaurantId, tabId, paymentId, actingUserId, request);
        }

        if (payment.getStatus() != PaymentStatus.ACTIVE) {
            throw new IllegalArgumentException("Payment is already voided.");
        }
        if (cardCharge.getRefundedAt() != null) {
            throw new IllegalStateException("This card payment was already refunded.");
        }

        CardIntegration integration = cardIntegrationRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalStateException("This restaurant's card payment is no longer configured."));
        String accessToken = credentialEncryptionService.decrypt(integration.getAccessTokenEncrypted());

        String refundId = mercadoPagoApiClient.refundPayment(accessToken, cardCharge.getMpPaymentId());

        cardCharge.setRefundedAt(OffsetDateTime.now());
        cardCharge.setRefundId(refundId);
        cardChargeRepository.save(cardCharge);

        return tabService.voidPayment(restaurantId, tabId, paymentId, actingUserId, request);
    }

    private PaymentMethod mapPaymentMethod(String paymentTypeId) {
        return "debit_card".equals(paymentTypeId) ? PaymentMethod.DEBIT_CARD : PaymentMethod.CREDIT_CARD;
    }

    private String extractPaymentId(byte[] rawBody) {
        try {
            JsonNode root = objectMapper.readTree(new String(rawBody, StandardCharsets.UTF_8));
            String id = root.path("data").path("id").asText(null);
            return (id == null || id.isBlank()) ? null : id;
        } catch (Exception e) {
            throw new CardGatewayException("Failed to parse Mercado Pago webhook payload.", e);
        }
    }

    private String buildNotificationUrl(UUID restaurantId) {
        return backendUrl + "/api/v1/public/payments/mercadopago/webhook/" + restaurantId;
    }

    // ref carries the charge's own externalReference so CardPaymentReturnPage can trigger
    // #verifyPendingChargeByExternalReference the moment the paying browser lands back here -
    // see that method's Javadoc for why embedding our own opaque id (never anything Mercado Pago
    // appends) keeps this safe.
    private String buildStaffReturnUrl(String status, String externalReference) {
        return frontendUrl + "/pagamento/retorno?status=" + status + "&ref=" + externalReference;
    }

    private String buildMenuReturnUrl(String slug, UUID tableId, String status, String externalReference) {
        return frontendUrl + "/pagamento/retorno?status=" + status + "&menu=" + slug + "&table=" + tableId + "&ref=" + externalReference;
    }

    private String buildDeliveryReturnUrl(String accessToken, String status, String externalReference) {
        return frontendUrl + "/pagamento/retorno?status=" + status + "&delivery=" + accessToken + "&ref=" + externalReference;
    }

    private CardChargeResponse toResponse(CardCharge charge) {
        return CardChargeResponse.builder()
                .id(charge.getId())
                .amount(charge.getAmount())
                .initPointUrl(charge.getInitPointUrl())
                .externalReference(charge.getExternalReference())
                .status(charge.getStatus())
                .declineMessage(charge.getStatus() == CardChargeStatus.DECLINED
                        ? CardDeclineMessages.forStatusDetail(charge.getStatusDetail())
                        : null)
                .build();
    }
}
