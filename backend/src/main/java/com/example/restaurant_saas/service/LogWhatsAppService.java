package com.example.restaurant_saas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Writes the notification to the application log instead of sending a real WhatsApp message.
 * Used in dev/test, and as the default until a real provider is configured — see
 * {@link MetaWhatsAppService} for the production implementation, selected via {@code app.whatsapp.provider}.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.whatsapp", name = "provider", havingValue = "log", matchIfMissing = true)
public class LogWhatsAppService implements WhatsAppService {

    @Override
    public void sendOrderReadyNotification(String toPhoneNumber, String restaurantName) {
        log.info("WhatsApp order-ready notification to {} for {}", toPhoneNumber, restaurantName);
    }
}
