package com.example.restaurant_saas.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class HaversineUtilTest {

    @Test
    void distanceKm_forSamePoint_shouldBeZero() {
        assertThat(HaversineUtil.distanceKm(-23.5505, -46.6333, -23.5505, -46.6333)).isCloseTo(0.0, offset(0.001));
    }

    @Test
    void distanceKm_forKnownPoints_shouldMatchExpectedDistance() {
        // Praca da Se to Av. Paulista/Consolacao (Sao Paulo) - roughly 3km apart in a straight
        // line, verified against an independent haversine calculator.
        double distance = HaversineUtil.distanceKm(-23.5505, -46.6333, -23.5629, -46.6544);

        assertThat(distance).isCloseTo(2.55, offset(0.05));
    }

    @Test
    void distanceKm_isSymmetric() {
        double ab = HaversineUtil.distanceKm(-23.5505, -46.6333, -23.5629, -46.6544);
        double ba = HaversineUtil.distanceKm(-23.5629, -46.6544, -23.5505, -46.6333);

        assertThat(ab).isCloseTo(ba, offset(0.0001));
    }
}
