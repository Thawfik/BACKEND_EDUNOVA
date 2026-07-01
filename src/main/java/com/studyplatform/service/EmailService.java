package com.studyplatform.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional emails (currently the bilingual FR/EN OTP welcome mail).
 *
 * Delivery is attempted in this order:
 *   1. Resend HTTP API — works on hosts that block outbound SMTP, notably
 *      Render's free tier (ports 25/465/587 are blocked there since 09/2025).
 *   2. Gmail SMTP — handy for local development.
 * With neither configured the code is logged instead of sent, so local dev
 * works without any credentials.
 */
@Service
@Slf4j
public class EmailService {

    private static final String SUBJECT =
            "EduNova+ — Votre code de vérification / Your verification code";

    private final JavaMailSender mailSender;
    private final RestClient restClient = RestClient.create();

    @Value("${app.mail.from}")
    private String from;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${app.mail.resend.api-key:}")
    private String resendApiKey;

    @Value("${app.mail.resend.url:https://api.resend.com/emails}")
    private String resendUrl;

    @Value("${app.otp.expiry-minutes:10}")
    private int expiryMinutes;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String to, String firstName, String code) {
        String html = buildHtml(firstName, code);

        if (resendApiKey != null && !resendApiKey.isBlank()) {
            sendViaResend(to, html, code);
        } else if (smtpUsername != null && !smtpUsername.isBlank()) {
            sendViaSmtp(to, html, code);
        } else {
            log.warn("[DEV] Email not configured (no RESEND_API_KEY / SMTP) — OTP for {} is: {}", to, code);
        }
    }

    /** Delivery over HTTPS — unaffected by SMTP port blocking on Render's free tier. */
    private void sendViaResend(String to, String html, String code) {
        try {
            Map<String, Object> body = Map.of(
                    "from", from,
                    "to", List.of(to),
                    "subject", SUBJECT,
                    "html", html);
            restClient.post()
                    .uri(resendUrl)
                    .header("Authorization", "Bearer " + resendApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("OTP email sent to {} via Resend", to);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {} via Resend: {}", to, e.getMessage());
            log.warn("[FALLBACK] OTP for {} is: {}", to, code);
        }
    }

    private void sendViaSmtp(String to, String html, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("OTP email sent to {} via SMTP", to);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {} via SMTP: {}", to, e.getMessage());
            log.warn("[FALLBACK] OTP for {} is: {}", to, code);
        }
    }

    private String buildHtml(String firstName, String code) {
        String name = firstName != null && !firstName.isBlank() ? firstName : "";
        return """
            <div style="margin:0;padding:0;background:#f3f4f6;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
              <div style="max-width:560px;margin:0 auto;padding:32px 16px;">
                <div style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.06);">
                  <div style="background:linear-gradient(135deg,#4f46e5,#7c3aed);padding:28px 32px;color:#fff;">
                    <span style="font-size:22px;font-weight:700;letter-spacing:-0.5px;">EduNova<sup>+</sup></span>
                  </div>
                  <div style="padding:32px;">
                    <h1 style="margin:0 0 8px;font-size:20px;color:#111827;">Bienvenue %s ! 🎉</h1>
                    <p style="margin:0 0 20px;font-size:15px;color:#4b5563;line-height:1.6;">
                      Nous sommes ravis de vous accueillir sur <b>EduNova+</b>. Pour finaliser votre
                      inscription, saisissez le code de vérification ci-dessous.
                    </p>
                    <div style="background:#f9fafb;border:1px dashed #c7d2fe;border-radius:12px;text-align:center;padding:20px;margin:0 0 20px;">
                      <div style="font-size:34px;font-weight:800;letter-spacing:10px;color:#4f46e5;">%s</div>
                    </div>
                    <p style="margin:0 0 24px;font-size:13px;color:#6b7280;">
                      Ce code expire dans %d minutes. Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.
                    </p>
                    <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0;">
                    <h2 style="margin:0 0 8px;font-size:18px;color:#111827;">Welcome %s! 🎉</h2>
                    <p style="margin:0 0 16px;font-size:15px;color:#4b5563;line-height:1.6;">
                      We're delighted to welcome you to <b>EduNova+</b>. To complete your
                      registration, enter the verification code shown above.
                    </p>
                    <p style="margin:0;font-size:13px;color:#6b7280;">
                      This code expires in %d minutes. If you didn't request this, please ignore this email.
                    </p>
                  </div>
                  <div style="background:#f9fafb;padding:18px 32px;text-align:center;font-size:12px;color:#9ca3af;">
                    © 2026 EduNova+ — Conçu pour les étudiants africains
                  </div>
                </div>
              </div>
            </div>
            """.formatted(name, code, expiryMinutes, name, expiryMinutes);
    }
}
