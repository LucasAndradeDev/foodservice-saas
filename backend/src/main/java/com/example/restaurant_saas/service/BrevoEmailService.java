package com.example.restaurant_saas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends transactional email through Brevo's REST API (no SDK needed — a plain
 * authenticated POST, same style as {@link SupabaseFileStorageService}).
 */
@Service
@ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "brevo")
public class BrevoEmailService implements EmailService {

    // Styled text instead of an <img>: Gmail (and several other clients) strip
    // data-URI/base64 images from received mail, and we don't have a public HTTPS
    // URL to host a real image file yet — text always renders, no image to block.
    private static final String LOGO_HTML = """
            <span style="font-family:Arial,Helvetica,sans-serif;font-size:28px;font-weight:bold;">
              <span style="color:#3a2a1f;">mor</span><span style="color:#d9662d;">á</span>
            </span>
            """;

    private static final String SHELL = """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f5f1ee;padding:40px 16px;font-family:Arial,Helvetica,sans-serif;">
              <tr>
                <td align="center">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background-color:#ffffff;border-radius:16px;overflow:hidden;">
                    <tr>
                      <td style="padding:36px 40px 4px 40px;text-align:center;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:20px 40px 0 40px;text-align:center;font-size:38px;line-height:1;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:12px 40px 0 40px;text-align:center;">
                        <h1 style="margin:0;font-size:20px;color:#2c2117;">%s</h1>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:12px 40px 28px 40px;text-align:center;">
                        <p style="margin:0;font-size:14px;line-height:1.6;color:#5c5248;">%s</p>
                      </td>
                    </tr>
                    %s
                    <tr>
                      <td style="padding:0 40px 36px 40px;text-align:center;">
                        <p style="margin:0;font-size:12px;color:#a39a8f;">%s</p>
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>
            </table>
            """;

    private static final String BUTTON_ROW = """
            <tr>
              <td style="padding:0 40px 32px 40px;text-align:center;">
                <a href="%s" style="display:inline-block;background-color:#b3421f;color:#ffffff;text-decoration:none;font-size:15px;font-weight:bold;padding:14px 32px;border-radius:8px;">%s</a>
              </td>
            </tr>
            """;

    private final RestClient restClient;
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailService(
            @Value("${brevo.api-key}") String apiKey,
            @Value("${brevo.sender-email}") String senderEmail,
            @Value("${brevo.sender-name}") String senderName
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", apiKey)
                .build();
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        String buttonRow = BUTTON_ROW.formatted(resetLink, "Criar nova senha");
        String html = SHELL.formatted(
                LOGO_HTML,
                "🔐",
                "Redefinir sua senha",
                "Recebemos um pedido para redefinir a senha da sua conta Morá. Clique no botão abaixo para criar uma nova senha.",
                buttonRow,
                "Esse link expira em 30 minutos. Se você não pediu essa alteração, pode ignorar este email — sua senha continua a mesma."
        );

        send(toEmail, "Recuperação de senha - Morá", html);
    }

    @Override
    public void sendPasswordChangedNotification(String toEmail) {
        String html = SHELL.formatted(
                LOGO_HTML,
                "✅",
                "Sua senha foi alterada",
                "A senha da sua conta Morá foi alterada com sucesso.",
                "",
                "Se não foi você quem fez essa alteração, entre em contato com quem administra sua conta Morá o quanto antes."
        );

        send(toEmail, "Senha alterada - Morá", html);
    }

    private void send(String toEmail, String subject, String htmlContent) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("email", senderEmail, "name", senderName),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "htmlContent", htmlContent
        );

        restClient.post()
                .uri("/smtp/email")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
