package com.itt.sareduccion.security;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import com.itt.sareduccion.security.logging.SensitiveDataMasker;

/**
 * Entrega de codigos OTP para aspirantes via SMTP cuando la configuracion de
 * correo esta activa ({@code SPRING_MAIL_HOST} y {@code SAR_MAIL_FROM}). En
 * desarrollo local sin SMTP configurado, el bean no se registra para conservar
 * el respaldo controlado en bitacora ({@code sar.security.otp.log-code=true}).
 */
@Component
@ConditionalOnExpression(
        "!'${spring.mail.host:${SPRING_MAIL_HOST:}}'.trim().isEmpty() && !'${SAR_MAIL_FROM:}'.trim().isEmpty()")
public class CorreoOtpDelivery implements OtpDelivery {

    private static final Logger LOG = LoggerFactory.getLogger(CorreoOtpDelivery.class);

    private final JavaMailSender mailSender;
    private final String remitente;

    public CorreoOtpDelivery(JavaMailSender mailSender, @Value("${SAR_MAIL_FROM:}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente == null ? "" : remitente.trim();
    }

    @Override
    public void enviar(String correo, String codigo, int minutosVigencia) {
        if (!StringUtils.hasText(remitente) || !StringUtils.hasText(correo) || !StringUtils.hasText(codigo)) {
            throw new IllegalStateException("Configuracion o datos insuficientes para enviar el codigo OTP");
        }
        int vigencia = Math.max(1, minutosVigencia);
        String texto = "Tu codigo de verificacion para ingresar a SAReduccion es: " + codigo + "\n\n"
                + "Este codigo vence en " + vigencia + " minuto(s) y es de un solo uso.\n"
                + "Si no solicitaste este acceso, ignora este mensaje.";
        String html = "<div style='font-family:Arial,sans-serif;max-width:560px;margin:0 auto;"
                + "border:1px solid #d9e2ec;border-radius:8px;overflow:hidden'>"
                + "<div style='background:#1b396a;color:#ffffff;padding:16px 22px'>"
                + "<h2 style='margin:0;font-size:18px'>SAReducción | Código de verificación</h2>"
                + "</div>"
                + "<div style='padding:22px;color:#243b53;line-height:1.6'>"
                + "<p>Utiliza el siguiente código temporal de un solo uso para completar tu inicio de sesión:</p>"
                + "<div style='margin:18px 0;padding:14px 18px;background:#f2f6fa;border-left:4px solid #17665e;"
                + "font-size:24px;font-weight:700;letter-spacing:4px;color:#102a43'>"
                + HtmlUtils.htmlEscape(codigo)
                + "</div>"
                + "<p>Vigencia: <b>" + vigencia + " minuto(s)</b>.</p>"
                + "<p style='font-size:13px;color:#627d98'>Si no solicitaste este código, puedes ignorar este correo.</p>"
                + "</div>"
                + "</div>";
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(correo.trim());
            helper.setSubject("SAReducción | Código de verificación");
            helper.setText(texto, html);
            mailSender.send(mensaje);
            LOG.info("OTP enviado por correo a {}", SensitiveDataMasker.email(correo));
        } catch (MessagingException | MailException ex) {
            LOG.warn("No se pudo enviar OTP a {} ({})",
                    SensitiveDataMasker.email(correo), ex.getClass().getSimpleName());
            throw new IllegalStateException("No se pudo enviar el codigo de verificacion por correo", ex);
        }
    }
}
