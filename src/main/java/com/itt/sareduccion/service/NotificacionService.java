package com.itt.sareduccion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.itt.sareduccion.repository.AdministradorRepository;

@Service
public class NotificacionService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacionService.class);

    private final AdministradorRepository administradorRepository;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;

    public NotificacionService(AdministradorRepository administradorRepository, ObjectProvider<JavaMailSender> mailSender,
            @Value("${SAR_MAIL_FROM:}") String remitente) {
        this.administradorRepository = administradorRepository;
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    public enum Evento {
        APROBACION("Tu solicitud fue aprobada."),
        RECHAZO("Tu solicitud fue rechazada."),
        CANCELACION("La deducción de tu solicitud fue cancelada.");

        private final String texto;

        Evento(String texto) {
            this.texto = texto;
        }
    }

    /** El repositorio ya confirmó el cambio de estado antes de enviar el aviso. */
    public void avisarCambio(String idAdministrador, long idSolicitud, Evento evento) {
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
        enviar(idSolicitud, destinatario, evento.texto
                + "\n\nConsulta el estado y los detalles en el portal de SAReduccion.");
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
