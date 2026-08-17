package com.example.restaurant_saas.dto.response;

import com.example.restaurant_saas.domain.enums.CardChargeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardChargeResponse {
    private UUID id;
    private BigDecimal amount;
    private String initPointUrl;
    // Not sensitive - Mercado Pago itself already has it (it's the value we handed over as
    // external_reference when creating the preference), and it's already embedded in the public
    // back_url's `ref` query param. Lets the Caixa's own polling loop
    // (CheckoutPage's cardPolledTab) call POST /public/payments/mercadopago/verify/{ref} itself
    // instead of only waiting on the customer's browser to redirect back - see
    // docs/CARD_PAYMENT.md, "Segundo caminho pra confirmação".
    private String externalReference;
    // Only meaningful on the list endpoint (PENDING/PAID/DECLINED) - a freshly created charge is
    // always PENDING, so callers of createCharge don't need to check it there.
    private CardChargeStatus status;
    // Short, specific Portuguese message derived server-side from Mercado Pago's raw status_detail
    // code (see CardDeclineMessages) - null unless status is DECLINED. The frontend never sees the
    // raw gateway code.
    private String declineMessage;
}
