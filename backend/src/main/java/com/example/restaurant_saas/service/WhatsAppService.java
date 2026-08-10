package com.example.restaurant_saas.service;

public interface WhatsAppService {
    void sendOrderReadyNotification(String toPhoneNumber, String restaurantName);
}
