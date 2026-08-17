package com.example.restaurant_saas.service;

import java.util.Map;

/**
 * Maps Mercado Pago's raw {@code status_detail} codes to short, specific Portuguese messages
 * telling the customer what to actually do - instead of a generic "payment failed". Single source
 * of truth shared by both the Caixa and digital-menu flows, since both read this off
 * {@link com.example.restaurant_saas.dto.response.CardChargeResponse#getDeclineMessage()} rather
 * than keeping their own copy of gateway-specific codes.
 *
 * <p>Codes captured from developers.mercadopago.com during design - worth re-checking spelling
 * against a real sandbox decline before relying on this for a live restaurant.
 */
final class CardDeclineMessages {

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("cc_rejected_insufficient_amount", "Saldo ou limite insuficiente. Tente outro cartão."),
            Map.entry("cc_rejected_bad_filled_security_code", "Código de segurança incorreto. Confira o CVV e tente de novo."),
            Map.entry("cc_rejected_bad_filled_date", "Data de validade incorreta."),
            Map.entry("cc_rejected_bad_filled_card_number", "Número do cartão incorreto. Confira e tente de novo."),
            Map.entry("cc_rejected_bad_filled_other", "Algum dado do cartão está incorreto. Confira e tente de novo."),
            Map.entry("cc_rejected_call_for_authorize", "O banco pediu autorização manual. Ligue pro seu banco ou use outro cartão."),
            Map.entry("cc_rejected_card_disabled", "Cartão desabilitado para compras online. Contate seu banco ou tente outro cartão."),
            Map.entry("cc_rejected_duplicated_payment", "Já existe um pagamento igual recente. Se não foi você, tente outro cartão."),
            Map.entry("cc_rejected_high_risk", "Pagamento recusado por segurança. Tente outro cartão ou meio de pagamento."),
            Map.entry("cc_rejected_max_attempts", "Muitas tentativas com esse cartão. Tente outro cartão."),
            Map.entry("cc_rejected_other_reason", "Cartão recusado pelo banco emissor. Tente outro cartão.")
    );

    private static final String FALLBACK = "Pagamento recusado. Tente outro cartão.";

    private CardDeclineMessages() {
    }

    static String forStatusDetail(String statusDetail) {
        if (statusDetail == null) {
            return FALLBACK;
        }
        return MESSAGES.getOrDefault(statusDetail, FALLBACK);
    }
}
