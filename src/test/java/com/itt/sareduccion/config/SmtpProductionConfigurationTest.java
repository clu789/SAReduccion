package com.itt.sareduccion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpProductionConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class, MailSenderValidatorAutoConfiguration.class))
            .withUserConfiguration(SmtpProductionConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=smtp-production",
                    "spring.mail.test-connection=false",
                    "spring.mail.host=smtp.example.test",
                    "spring.mail.port=587",
                    "spring.mail.username=usuario",
                    "spring.mail.password=credencial-de-prueba",
                    "SAR_MAIL_FROM=avisos@example.test",
                    "spring.mail.properties.mail.smtp.auth=true",
                    "spring.mail.properties.mail.smtp.starttls.enable=true");

    @Test
    void aceptaStarttlsYConservaTiemposDeEspera() {
        context.run(result -> {
            assertThat(result).hasNotFailed();
            JavaMailSenderImpl sender = result.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getJavaMailProperties())
                    .containsEntry("mail.smtp.starttls.required", "true")
                    .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                    .containsEntry("mail.smtp.connectiontimeout", "5000");
        });
    }

    @Test
    void aceptaTlsImplicito() {
        context.withPropertyValues(
                "spring.mail.port=465",
                "spring.mail.properties.mail.smtp.ssl.enable=true",
                "spring.mail.properties.mail.smtp.starttls.enable=false")
                .run(result -> assertThat(result).hasNotFailed());
    }

    @Test
    void aceptaRelayCifradoSinCredenciales() {
        context.withPropertyValues("spring.mail.properties.mail.smtp.auth=false",
                "spring.mail.username=", "spring.mail.password=")
                .run(result -> assertThat(result).hasNotFailed());
    }

    @Test
    void rechazaCredencialesFaltantesSinExponerlas() {
        context.withPropertyValues("spring.mail.password=")
                .run(result -> assertThat(result.getStartupFailure())
                        .hasRootCauseMessage("SMTP_AUTH requiere usuario y contraseña"));
    }

    @Test
    void rechazaRemitenteInvalido() {
        context.withPropertyValues("SAR_MAIL_FROM=incorrecto")
                .run(result -> assertThat(result.getStartupFailure())
                        .hasRootCauseMessage("SAR_MAIL_FROM debe contener un remitente válido"));
    }

    @Test
    void rechazaModosTlsSimultaneos() {
        context.withPropertyValues("spring.mail.properties.mail.smtp.ssl.enable=true")
                .run(result -> assertThat(result.getStartupFailure())
                        .hasRootCauseMessage("SMTP requiere elegir TLS implícito o STARTTLS"));
    }

    @Test
    void exigeCifradoSalvoExcepcionExplicita() {
        context.withPropertyValues("spring.mail.properties.mail.smtp.starttls.enable=false")
                .run(result -> assertThat(result).hasFailed());
        context.withPropertyValues("spring.mail.properties.mail.smtp.starttls.enable=false",
                "SMTP_ALLOW_PLAINTEXT=true")
                .run(result -> assertThat(result).hasNotFailed());
    }

    @Test
    void fallaSiLaComprobacionDeConexionNoAlcanzaElServidor() {
        context.withPropertyValues("spring.mail.test-connection=true",
                "spring.mail.host=127.0.0.1", "spring.mail.port=1",
                "spring.mail.properties.mail.smtp.connectiontimeout=200")
                .run(result -> assertThat(result).hasFailed());
    }

    @Test
    void rechazaPuertoFueraDeRango() {
        context.withPropertyValues("spring.mail.port=0")
                .run(result -> assertThat(result).hasFailed());
    }

    @Test
    void rechazaVerificacionTlsDesactivada() {
        context.withPropertyValues("spring.mail.properties.mail.smtp.ssl.checkserveridentity=false")
                .run(result -> assertThat(result).hasFailed());
    }
}
