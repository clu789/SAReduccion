package com.itt.sareduccion.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class CorreoOtpDeliveryTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withUserConfiguration(CorreoOtpDelivery.class);

    @Test
    void seActivaSoloCuandoHostYRemitenteEstanConfigurados() {
        runner.run(ctx -> assertFalse(ctx.containsBean("correoOtpDelivery")));

        runner.withPropertyValues("SPRING_MAIL_HOST=", "SAR_MAIL_FROM=")
                .run(ctx -> assertFalse(ctx.containsBean("correoOtpDelivery")));

        runner.withPropertyValues("SPRING_MAIL_HOST=smtp.example.test", "SAR_MAIL_FROM=")
                .run(ctx -> assertFalse(ctx.containsBean("correoOtpDelivery")));

        runner.withPropertyValues("SPRING_MAIL_HOST=smtp.example.test", "SAR_MAIL_FROM=sar@example.test")
                .run(ctx -> {
                    assertTrue(ctx.containsBean("correoOtpDelivery"));
                    assertTrue(ctx.getBean(OtpDelivery.class) instanceof CorreoOtpDelivery);
                });
    }

    @Test
    void construyeYEnviaCorreoHtmlYTextoConCodigoYVigencia() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);

        CorreoOtpDelivery delivery = new CorreoOtpDelivery(sender, "sar@example.test");
        delivery.enviar("aspirante@example.test", "482910", 10);

        verify(sender).send(message);
        message.saveChanges();
        String html = content(message, "text/html");
        String plain = content(message, "text/plain");
        assertTrue(message.getSubject().contains("verificación"));
        assertTrue(html.contains("482910"));
        assertTrue(html.contains("10 minuto(s)"));
        assertTrue(plain.contains("482910"));
        assertTrue(plain.contains("10 minuto(s)"));
    }

    @Test
    void envuelveFalloSmtpEnExcepcionDeTiempoDeEjecucion() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("SMTP no disponible")).when(sender).send(any(MimeMessage.class));

        CorreoOtpDelivery delivery = new CorreoOtpDelivery(sender, "sar@example.test");
        assertThrows(IllegalStateException.class,
                () -> delivery.enviar("aspirante@example.test", "123456", 5));
        assertThrows(IllegalStateException.class,
                () -> delivery.enviar("", "123456", 5));
        assertThrows(IllegalStateException.class,
                () -> new CorreoOtpDelivery(sender, "").enviar("aspirante@example.test", "123456", 5));
    }

    private String content(Part part, String type) throws Exception {
        if (part.isMimeType(type)) {
            return part.getContent().toString();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart parts = (Multipart) part.getContent();
            for (int i = 0; i < parts.getCount(); i++) {
                String found = content(parts.getBodyPart(i), type);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }
}
