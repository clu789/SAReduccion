package com.itt.sareduccion.config;

import java.util.Properties;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

/** Valida los valores efectivos del contenedor antes de aceptar tráfico. */
@Configuration
@Profile("smtp-production")
public class SmtpProductionConfiguration {

    public SmtpProductionConfiguration(ObjectProvider<JavaMailSender> provider,
            @Value("${SAR_MAIL_FROM:}") String from,
            @Value("${SMTP_ALLOW_PLAINTEXT:false}") boolean allowPlaintext) {
        JavaMailSender sender = provider.getIfAvailable();
        if (!(sender instanceof JavaMailSenderImpl)) {
            throw new IllegalStateException("SMTP requiere SPRING_MAIL_HOST y JavaMailSender configurados");
        }
        JavaMailSenderImpl smtp = (JavaMailSenderImpl) sender;
        if (!StringUtils.hasText(smtp.getHost()) || smtp.getPort() < 1 || smtp.getPort() > 65535) {
            throw new IllegalStateException("SMTP requiere host y puerto válidos");
        }
        try {
            InternetAddress[] addresses = InternetAddress.parse(from, true);
            if (addresses.length != 1 || !addresses[0].getAddress().contains("@")) {
                throw new AddressException();
            }
            addresses[0].validate();
        } catch (AddressException ex) {
            throw new IllegalStateException("SAR_MAIL_FROM debe contener un remitente válido");
        }
        Properties properties = smtp.getJavaMailProperties();
        boolean ssl = Boolean.parseBoolean(properties.getProperty("mail.smtp.ssl.enable", "false"));
        boolean starttls = Boolean.parseBoolean(properties.getProperty("mail.smtp.starttls.enable", "false"));
        if (ssl && starttls) {
            throw new IllegalStateException("SMTP requiere elegir TLS implícito o STARTTLS");
        }
        if (!ssl && !starttls && !allowPlaintext) {
            throw new IllegalStateException("SMTP requiere cifrado o SMTP_ALLOW_PLAINTEXT explícito");
        }
        if (Boolean.parseBoolean(properties.getProperty("mail.smtp.auth", "false"))
                && (!StringUtils.hasText(smtp.getUsername()) || !StringUtils.hasText(smtp.getPassword()))) {
            throw new IllegalStateException("SMTP_AUTH requiere usuario y contraseña");
        }
        if (starttls && !Boolean.parseBoolean(properties.getProperty("mail.smtp.starttls.required", "false"))) {
            throw new IllegalStateException("STARTTLS debe ser obligatorio en producción");
        }
        if ((ssl || starttls)
                && !Boolean.parseBoolean(properties.getProperty("mail.smtp.ssl.checkserveridentity", "false"))) {
            throw new IllegalStateException("SMTP requiere verificar la identidad TLS del servidor");
        }
    }
}
