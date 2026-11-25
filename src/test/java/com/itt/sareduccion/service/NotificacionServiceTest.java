package com.itt.sareduccion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import javax.mail.Part;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import com.itt.sareduccion.repository.AdministradorRepository;

class NotificacionServiceTest {
    @Test
    void plantillasIncluyenEstadoYComentarioSeguroConTextoAlternativo() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificacionService service = new NotificacionService(
                mock(AdministradorRepository.class), providerDe(sender), "sar@example.test");
        for (NotificacionService.Evento evento : NotificacionService.Evento.values()) {
            for (String comentario : new String[] {null, "Revisar <script>ejemplo</script>\nGracias"}) {
                MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
                when(sender.createMimeMessage()).thenReturn(message);
                assertTrue(service.enviarPlantilla(42, "aspirante@example.test", evento, comentario));
                message.saveChanges();
                String html = content(message, "text/html");
                String plain = content(message, "text/plain");
                assertTrue(message.getSubject().contains("42"));
                assertTrue(html.contains("SOLICITUD 42"));
                assertFalse(html.contains("{{"));
                assertFalse(html.contains("<script>"));
                assertFalse(plain.isEmpty());
                if (comentario == null) {
                    assertFalse(html.contains("Comentario del administrador"));
                } else {
                    assertTrue(html.contains("&lt;script&gt;"));
                    assertTrue(plain.contains(comentario));
                }
                verify(sender).send(message);
            }
        }
    }

    @Test
    void avisarCambioPropagaMdcEnEjecutorAsincronoYNoLanzaExcepcionSiFallaRepositorio() {
        AdministradorRepository repo = mock(AdministradorRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);

        AtomicReference<String> correlationCapturado = new AtomicReference<String>();
        when(repo.consultarCorreoSolicitud("SA01", 77L)).thenAnswer(inv -> {
            correlationCapturado.set(MDC.get("correlationId"));
            return "aspirante@example.test";
        });

        NotificacionService service = new NotificacionService(repo, providerDe(sender), "sar@example.test", Runnable::run);
        MDC.put("correlationId", "req-notif-123");
        try {
            service.avisarCambio("SA01", 77L, NotificacionService.Evento.APROBACION, "Listo");
        } finally {
            MDC.clear();
        }
        assertEquals("req-notif-123", correlationCapturado.get());
        verify(sender).send(message);

        doThrow(new org.springframework.dao.DataAccessResourceFailureException("db caida"))
                .when(repo).consultarCorreoSolicitud("SA01", 78L);
        assertDoesNotThrow(() -> service.avisarCambio("SA01", 78L, NotificacionService.Evento.RECHAZO, "Motivo"));
    }

    private String content(Part part, String type) throws Exception {
        if (part.isMimeType(type)) return part.getContent().toString();
        if (part.isMimeType("multipart/*")) {
            Multipart parts = (Multipart) part.getContent();
            for (int i = 0; i < parts.getCount(); i++) {
                String found = content(parts.getBodyPart(i), type);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private ObjectProvider<JavaMailSender> providerDe(final JavaMailSender sender) {
        return new ObjectProvider<JavaMailSender>() {
            @Override
            public JavaMailSender getObject() {
                return sender;
            }

            @Override
            public JavaMailSender getObject(Object... args) {
                return sender;
            }

            @Override
            public JavaMailSender getIfAvailable() {
                return sender;
            }

            @Override
            public JavaMailSender getIfUnique() {
                return sender;
            }
        };
    }
}
