package com.example.restaurant_saas.service;

import java.util.List;

/**
 * Wire-format shape parsed out of the Gemini response for menu extraction.
 * Public so tests can construct a stubbed {@link GeminiService#extractMenu} return value.
 */
public record GeminiExtractionResult(List<GeminiCategory> categories) {
}
