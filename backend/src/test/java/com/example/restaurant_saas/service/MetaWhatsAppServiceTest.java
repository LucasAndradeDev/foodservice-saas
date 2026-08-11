package com.example.restaurant_saas.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetaWhatsAppServiceTest {

    @Test
    void normalizePhone_withBrazilianFormattingAndNoCountryCode_addsCountryCode() {
        assertThat(MetaWhatsAppService.normalizePhone("(11) 91234-5678")).isEqualTo("5511912345678");
    }

    @Test
    void normalizePhone_withCountryCodeAndPlusSign_keepsOnlyDigits() {
        assertThat(MetaWhatsAppService.normalizePhone("+55 11 91234-5678")).isEqualTo("5511912345678");
    }

    @Test
    void normalizePhone_withPlainElevenDigitsAndNoCountryCode_addsCountryCode() {
        assertThat(MetaWhatsAppService.normalizePhone("11912345678")).isEqualTo("5511912345678");
    }

    @Test
    void normalizePhone_withTenDigitsAndNoCountryCode_addsCountryCode() {
        // Older landline-style number: area code + 8-digit local number, no mobile "9".
        assertThat(MetaWhatsAppService.normalizePhone("1132345678")).isEqualTo("551132345678");
    }

    @Test
    void normalizePhone_withCountryCodeAlreadyPresent_isUnchanged() {
        assertThat(MetaWhatsAppService.normalizePhone("5511912345678")).isEqualTo("5511912345678");
    }

    @Test
    void normalizePhone_withSpacesAndDotsAndNoCountryCode_addsCountryCode() {
        assertThat(MetaWhatsAppService.normalizePhone("11 9.1234.5678")).isEqualTo("5511912345678");
    }

    @Test
    void normalizePhone_withUnrecognizedLength_isLeftAsIs() {
        // Neither a bare 10/11-digit local number nor already carrying a country code - left
        // as-is rather than guessed at.
        assertThat(MetaWhatsAppService.normalizePhone("123456")).isEqualTo("123456");
    }

    @Test
    void normalizePhone_withNoDigits_returnsEmptyString() {
        assertThat(MetaWhatsAppService.normalizePhone("()- ")).isEmpty();
    }
}
