package com.itt.sareduccion.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PreDestroy;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import com.itt.sareduccion.repository.AdministradorRepository;

@Service
public class NotificacionService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacionService.class);

    private final AdministradorRepository administradorRepository;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;
    private final String plantilla;
    private final Executor executor;
    private final boolean ownsExecutor;

    @Autowired
    public NotificacionService(AdministradorRepository administradorRepository, ObjectProvider<JavaMailSender> mailSender,
            @Value("${SAR_MAIL_FROM:}") String remitente) {
        this(administradorRepository, mailSender, remitente, crearEjecutorPorDefecto(), true);
    }

    NotificacionService(AdministradorRepository administradorRepository, ObjectProvider<JavaMailSender> mailSender,
            String remitente, Executor executor) {
        this(administradorRepository, mailSender, remitente, executor, false);
    }

    private NotificacionService(AdministradorRepository administradorRepository, ObjectProvider<JavaMailSender> mailSender,
            String remitente, Executor executor, boolean ownsExecutor) {
        this.administradorRepository = administradorRepository;
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
        try (InputStream archivo = new ClassPathResource("mail/aviso-tramite.html").getInputStream()) {
            this.plantilla = StreamUtils.copyToString(archivo, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo cargar la plantilla de notificaciones", ex);
        }
    }

    @PreDestroy
    void cerrarEjecutor() {
        if (ownsExecutor && executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
        }
    }

    private static ExecutorService crearEjecutorPorDefecto() {
        AtomicInteger secuencia = new AtomicInteger(1);
        ThreadFactory factory = runnable -> {
            Thread hilo = new Thread(runnable, "sar-notificacion-" + secuencia.getAndIncrement());
            hilo.setDaemon(true);
            return hilo;
        };
        return new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(256), factory, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public enum Evento {
        APROBACION("Tu solicitud fue aprobada."),
        RECHAZO("Tu solicitud fue rechazada."),
        CANCELACION("La deducción de tu solicitud fue cancelada."),
        APLICACION("La deducción de tu solicitud fue aplicada.");

        private final String texto;

        Evento(String texto) {
            this.texto = texto;
        }
    }

    /** El repositorio ya confirmó el cambio de estado antes de enviar el aviso. */
    public void avisarCambio(String idAdministrador, long idSolicitud, Evento evento) {
        avisarCambio(idAdministrador, idSolicitud, evento, null);
    }

    public void avisarCambio(String idAdministrador, long idSolicitud, Evento evento, String comentario) {
        final Map<String, String> contextoMdc = MDC.getCopyOfContextMap();
        try {
            executor.execute(() -> {
                if (contextoMdc != null) {
                    MDC.setContextMap(contextoMdc);
                } else {
                    MDC.clear();
                }
                try {
                    ejecutarAvisoCambio(idAdministrador, idSolicitud, evento, comentario);
                } catch (RuntimeException ex) {
                    LOG.warn("Error inesperado al enviar aviso {} para solicitud {} ({})",
                            evento, idSolicitud, ex.getClass().getSimpleName());
                } finally {
                    MDC.clear();
                }
            });
        } catch (RejectedExecutionException ex) {
            LOG.warn("Aviso {} descartado por cierre del ejecutor para solicitud {}", evento, idSolicitud);
        }
    }

    private void ejecutarAvisoCambio(String idAdministrador, long idSolicitud, Evento evento, String comentario) {
        String destinatario;
        try {
            destinatario = administradorRepository.consultarCorreoSolicitud(idAdministrador, idSolicitud);
        } catch (DataAccessException ex) {
            LOG.warn("No se pudo consultar el destinatario de {} para solicitud {}", evento, idSolicitud);
            return;
        }
        if (!StringUtils.hasText(destinatario)) {
            LOG.info("Aviso {} omitido para solicitud {} sin correo", evento, idSolicitud);
            return;
        }
        enviarPlantilla(idSolicitud, destinatario, evento, comentario);
    }

    public boolean enviarPlantilla(long idSolicitud, String destinatario, Evento evento, String comentario) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || !StringUtils.hasText(remitente)) {
            LOG.warn("Correo sin configurar para solicitud {}", idSolicitud);
            return false;
        }
        String estado;
        switch (evento) {
            case APROBACION: estado = "Autorizado"; break;
            case RECHAZO: estado = "Rechazado"; break;
            case APLICACION: estado = "Aplicado"; break;
            default: estado = "Cancelado";
        }
        String observacion = StringUtils.hasText(comentario) ? comentario.trim() : "";
        String texto = evento.texto + (observacion.isEmpty() ? "" : "\n\nComentario del administrador\n" + observacion)
                + "\n\nConsulta el estado y los detalles en el portal de SAReduccion.";
        String color = evento == Evento.RECHAZO ? "#a32f35" : "#17665e";
        String bloqueComentario = observacion.isEmpty() ? "" :
                "<div style='padding:18px;background:#f2f6fa;border-left:4px solid #315e8c'>"
                + "<b>Comentario del administrador</b><p style='line-height:1.6'>"
                + HtmlUtils.htmlEscape(observacion).replace("\r\n", "\n").replace("\n", "<br>")
                + "</p></div>";
        String html = plantilla.replace("{{solicitud}}", Long.toString(idSolicitud))
                .replace("{{estado}}", estado)
                .replace("{{color}}", color)
                .replace("{{mensaje}}", HtmlUtils.htmlEscape(evento.texto))
                .replace("{{comentario}}", bloqueComentario);
        try {
            MimeMessage correo = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(correo, true, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject("SAReducción | " + estado + " | Solicitud " + idSolicitud);
            helper.setText(texto, html);
            sender.send(correo);
            LOG.info("Aviso {} aceptado por SMTP para solicitud {}", estado, idSolicitud);
            return true;
        } catch (MessagingException | MailException ex) {
            LOG.warn("Falló el aviso para solicitud {} ({})", idSolicitud, ex.getClass().getSimpleName());
            return false;
        }
    }

    public boolean enviar(long idSolicitud, String destinatario, String mensaje) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || !StringUtils.hasText(remitente)) {
            LOG.warn("Correo sin configurar para solicitud {}", idSolicitud);
            return false;
        }
        SimpleMailMessage correo = new SimpleMailMessage();
        correo.setFrom(remitente);
        correo.setTo(destinatario);
        correo.setSubject("SAReduccion | Aviso de solicitud " + idSolicitud);
        correo.setText(mensaje);

        try {
            sender.send(correo);
            LOG.info("Aviso aceptado por SMTP para solicitud {}", idSolicitud);
            return true;
        } catch (MailException ex) {
            LOG.warn("Falló el aviso para solicitud {} ({})", idSolicitud, ex.getClass().getSimpleName());
            return false;
        }
    }
}
