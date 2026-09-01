package com.example.restaurant_saas.support;

import com.example.restaurant_saas.security.RateLimitService;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Clears {@link RateLimitService} before every test method. Registered globally in
 * META-INF/spring.factories, so it applies to every {@code @SpringBootTest} class without each
 * one needing {@code @TestExecutionListeners}. Without this, the bean's in-memory state carries
 * over between test classes (Spring reuses the cached application context across the whole
 * suite), so a run with enough tests hitting the same rate-limited endpoint eventually trips it
 * for a test that never meant to exercise rate limiting.
 */
public class RateLimitResetTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        testContext.getApplicationContext()
                .getBeanProvider(RateLimitService.class)
                .ifAvailable(RateLimitService::clearAllForTests);
    }
}
