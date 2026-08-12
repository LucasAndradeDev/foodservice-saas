package com.example.restaurant_saas.service;

import com.example.restaurant_saas.config.TenantActivator;
import com.example.restaurant_saas.domain.entity.PixCharge;
import com.example.restaurant_saas.domain.entity.PixIntegration;
import com.example.restaurant_saas.domain.entity.Restaurant;
import com.example.restaurant_saas.domain.entity.RestaurantTable;
import com.example.restaurant_saas.domain.entity.Tab;
import com.example.restaurant_saas.domain.enums.ItemStatus;
import com.example.restaurant_saas.domain.enums.PaymentMethod;
import com.example.restaurant_saas.domain.enums.PixChargeStatus;
import com.example.restaurant_saas.dto.request.PaymentEntryRequest;
import com.example.restaurant_saas.dto.request.RegisterPaymentsRequest;
import com.example.restaurant_saas.dto.response.PixChargeResponse;
import com.example.restaurant_saas.dto.response.PixIntegrationStatusResponse;
import com.example.restaurant_saas.exception.PixGatewayException;
import com.example.restaurant_saas.repository.OrderItemRepository;
import com.example.restaurant_saas.repository.PixChargeRepository;
import com.example.restaurant_saas.repository.PixIntegrationRepository;
import com.example.restaurant_saas.repository.RestaurantRepository;
import com.example.restaurant_saas.repository.RestaurantTableRepository;
import com.example.restaurant_saas.repository.TabRepository;
import com.example.restaurant_saas.security.CredentialEncryptionService;
import com.example.restaurant_saas.security.WooviWebhookSignatureVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PixChargeService {

    private static final String CHARGE_COMPLETED_STATUS = "COMPLETED";

    private final PixIntegrationRepository pixIntegrationRepository;
    private final PixChargeRepository pixChargeRepository;
    private final TabRepository tabRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final OrderItemRepository orderItemRepository;
    private final CredentialEncryptionService credentialEncryptionService;
    private final WooviApiClient wooviApiClient;
    private final WooviWebhookSignatureVerifier signatureVerifier;
    private final TabService tabService;
    private final TenantActivator tenantActivator;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveIntegration(UUID restaurantId, String rawAppId) {
        PixIntegration integration = pixIntegrationRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> PixIntegration.builder().restaurantId(restaurantId).build());
        integration.setApiKeyEncrypted(credentialEncryptionService.encrypt(rawAppId));
        pixIntegrationRepository.save(integration);
    }

    @Transactional(readOnly = true)
    public PixIntegrationStatusResponse getIntegrationStatus(UUID restaurantId) {
        boolean configured = pixIntegrationRepository.findByRestaurantId(restaurantId).isPresent();
        return PixIntegrationStatusResponse.builder().configured(configured).build();
    }

    /**
     * Creates a Pix charge for a tab: freezes the tab's bill total (see
     * {@link TabService#freezeBillTotalForPixCharge}), asks Woovi for a QR code for that exact
     * amount, and records a PENDING {@link PixCharge} so the webhook can later find its way back
     * to this tenant. {@code allowServiceChargeOverride}/{@code requestedServiceChargePercentage}
     * pass straight through to the freeze - see its javadoc for who may set them.
     */
    @Transactional
    public PixChargeResponse createCharge(
            UUID restaurantId, UUID tabId, boolean allowServiceChargeOverride, BigDecimal requestedServiceChargePercentage
    ) {
        PixIntegration integration = pixIntegrationRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalStateException("This restaurant hasn't configured Pix payment yet."));

        BigDecimal amount = tabService.freezeBillTotalForPixCharge(
                restaurantId, tabId, allowServiceChargeOverride, requestedServiceChargePercentage);
        // Just a reference, no extra query: freezeBillTotalForPixCharge already validated the tab
        // exists, belongs to this restaurant and is in the right state.
        Tab tab = tabRepository.getReferenceById(tabId);

        String appId = credentialEncryptionService.decrypt(integration.getApiKeyEncrypted());
        String correlationId = UUID.randomUUID().toString();

        WooviApiClient.ChargeResult chargeResult = wooviApiClient.createCharge(
                appId, correlationId, amount, "Comanda " + tabId);

        PixCharge pixCharge = PixCharge.builder()
                .restaurantId(restaurantId)
                .tab(tab)
                .externalChargeId(correlationId)
                .amount(amount)
                .status(PixChargeStatus.PENDING)
                .build();
        pixCharge = pixChargeRepository.save(pixCharge);

        return PixChargeResponse.builder()
                .id(pixCharge.getId())
                .amount(amount)
                .brCode(chargeResult.brCode())
                .qrCodeImage(chargeResult.qrCodeImage())
                .paymentLinkUrl(chargeResult.paymentLinkUrl())
                .build();
    }

    /**
     * Same as {@link #createCharge}, but for a customer paying on their own from the digital menu
     * instead of staff generating the QR code from the Caixa - resolves the tab from the
     * restaurant's slug and table id (same public lookup as {@link PublicTableRequestService})
     * instead of trusting an authenticated staff session's restaurantId. Gated on the tab having
     * at least one DELIVERED item, same as the "Pedir a conta" request, so a customer can't spin
     * up a charge before anything has actually reached the table.
     */
    @Transactional
    public PixChargeResponse createChargeForTable(String slug, UUID tableId) {
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
                throw new IllegalArgumentException("Cannot start a Pix charge for a table with no delivered items yet.");
            }

            // No staff role behind a customer paying on their own from the digital menu - always the
            // restaurant's configured default, same as freezeBillTotalForPixCharge's other non-OWNER/
            // MANAGER callers.
            return createCharge(restaurant.getId(), tab.getId(), false, null);
        } finally {
            tenantActivator.deactivate();
        }
    }

    /**
     * Cancels any still-PENDING Pix charge(s) on this tab and unfreezes its bill total
     * ({@link TabService#unfreezeBillTotal}) so items/discount/service-charge become editable
     * again - for when a QR code goes unpaid (customer changed their mind, it expired, staff
     * generated one by mistake) and the tab needs to keep being worked on. A no-op if there's
     * nothing PENDING. Marking every matching row CANCELLED (not just the latest) matters because
     * regenerating the QR code leaves earlier attempts' rows behind still PENDING - each one is a
     * correlationID Woovi could still deliver a late webhook for.
     */
    @Transactional
    public void cancelPendingCharge(UUID restaurantId, UUID tabId) {
        List<PixCharge> pending = pixChargeRepository.findByTab_IdAndStatus(tabId, PixChargeStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        tabService.unfreezeBillTotal(restaurantId, tabId);

        pending.forEach(charge -> charge.setStatus(PixChargeStatus.CANCELLED));
        pixChargeRepository.saveAll(pending);
    }

    /**
     * Handles a Woovi webhook call. Only {@code OPENPIX:CHARGE_COMPLETED} events with a known
     * correlationID do anything; everything else (other event types, an unrecognized charge, a
     * charge that isn't PENDING anymore - already PAID, or CANCELLED/EXPIRED after the tab moved
     * on without it) is a silent no-op, not an error - Woovi retries on non-2xx, and none of those
     * cases are recoverable by retrying.
     */
    @Transactional
    public void handleWebhook(byte[] rawBody, String signatureHeader) {
        if (!signatureVerifier.isValid(rawBody, signatureHeader)) {
            throw new IllegalArgumentException("Invalid webhook signature.");
        }

        JsonNode root = parseBody(rawBody);
        JsonNode chargeNode = root.path("charge");
        String status = chargeNode.path("status").asText(null);
        String correlationId = chargeNode.path("correlationID").asText(null);

        if (!CHARGE_COMPLETED_STATUS.equals(status) || correlationId == null) {
            return;
        }

        // Bypasses RLS on purpose - this is the one lookup that genuinely can't know the tenant
        // up front, same reasoning as ReservationService#cancelByToken.
        PixCharge pixCharge = pixChargeRepository.findByExternalChargeIdBypassingRls(correlationId).orElse(null);
        if (pixCharge == null || pixCharge.getStatus() != PixChargeStatus.PENDING) {
            return;
        }

        tenantActivator.activate(pixCharge.getRestaurantId());
        try {
            pixCharge.setStatus(PixChargeStatus.PAID);
            pixChargeRepository.save(pixCharge);

            RegisterPaymentsRequest request = new RegisterPaymentsRequest();
            PaymentEntryRequest entry = new PaymentEntryRequest();
            entry.setPaymentMethod(PaymentMethod.PIX);
            entry.setAmount(pixCharge.getAmount());
            request.setPayments(List.of(entry));

            // No staff role/user behind this payment - it was confirmed by Woovi's webhook, not
            // registered by someone logged in. TabService#registerPayments treats a null role as
            // "no override allowed", and a null actingUserId as an unattributed (system) payment -
            // same as Order.createdBy for self-service orders.
            tabService.registerPayments(pixCharge.getRestaurantId(), pixCharge.getTab().getId(), null, null, request);
        } finally {
            tenantActivator.deactivate();
        }
    }

    private JsonNode parseBody(byte[] rawBody) {
        try {
            return objectMapper.readTree(new String(rawBody, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new PixGatewayException("Failed to parse Woovi webhook payload.", e);
        }
    }
}
