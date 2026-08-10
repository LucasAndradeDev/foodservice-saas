package com.example.restaurant_saas.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetaWhatsAppServiceTest {

    @Test
    void normalizePhone_withBrazilianFormatting_stripsEverythingButDigits() {
        assertThat(MetaWhatsAppService.normalizePhone("(11) 91234-5678")).isEqualTo("11912345678");
    }

    @Test
    void normalizePhone_withCountryCodeAndPlusSign_keepsOnlyDigits() {
        assertThat(MetaWhatsAppService.normalizePhone("+55 11 91234-5678")).isEqualTo("5511912345678");
    }

    @Test
    void normalizePhone_withPlainDigits_isUnchanged() {
        assertThat(MetaWhatsAppService.normalizePhone("11912345678")).isEqualTo("11912345678");
    }

    @Test
    void normalizePhone_withSpacesAndDots_stripsThemToo() {
        assertThat(MetaWhatsAppService.normalizePhone("11 9.1234.5678")).isEqualTo("11912345678");
    }

    @Test
    void normalizePhone_withNoDigits_returnsEmptyString() {
        assertThat(MetaWhatsAppService.normalizePhone("()- ")).isEmpty();
    }
}
