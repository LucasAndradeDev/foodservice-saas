package com.example.restaurant_saas.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LogWhatsAppServiceTest {

    private final LogWhatsAppService service = new LogWhatsAppService();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LogWhatsAppService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void sendOrderReadyNotification_logsPhoneAndRestaurantNameAtInfoLevel() {
        service.sendOrderReadyNotification("11999999999", "Point Burger");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("11999999999")
                .contains("Point Burger");
    }

    @Test
    void sendOrderReadyNotification_doesNotThrowWhenPhoneIsBlank() {
        service.sendOrderReadyNotification("", "Point Burger");

        assertThat(appender.list).hasSize(1);
    }
}
