package com.example.restaurant_saas.service;

/**
 * A single PDF or image file, ready to be sent to Gemini as an inline multimodal part.
 */
public record GeminiDocument(String mimeType, byte[] data) {
}
