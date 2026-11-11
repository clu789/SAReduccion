package com.itt.sareduccion.controller;

import java.util.Collections;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.auth.SessionPrincipal;
import com.itt.sareduccion.repository.AdministradorRepository;
import com.itt.sareduccion.service.NotificacionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificacionController {

    private final AdministradorRepository administradorRepository;
    private final NotificacionService notificacionService;

    public NotificacionController(AdministradorRepository administradorRepository,
            NotificacionService notificacionService) {
        this.administradorRepository = administradorRepository;
        this.notificacionService = notificacionService;
    }

    @PostMapping("/api/notificaciones")
    public ResponseEntity<?> enviarManual(@RequestBody Aviso aviso,
            @RequestHeader(value = SessionAttributes.CSRF_HEADER, required = false) String csrf,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        SessionPrincipal principal = SessionAttributes.principal(session);
        if (principal == null) {
            return error(HttpStatus.UNAUTHORIZED, "Inicia sesión para enviar un aviso.");
        }
        if (principal.getRole() != AuthRole.ADMIN || !SessionAttributes.validCsrf(session, csrf)) {
            return error(HttpStatus.FORBIDDEN, "Solicitud no autorizada.");
        }
        if (aviso.idSolicitud == null || aviso.idSolicitud <= 0 || aviso.idSolicitud > 9999999999L
                || !StringUtils.hasText(aviso.mensaje) || aviso.mensaje.length() > 2000) {
            return error(HttpStatus.BAD_REQUEST, "Indica una solicitud y un mensaje de hasta 2000 caracteres.");
        }
        String correo = administradorRepository.consultarCorreoSolicitud(principal.getIdentity(), aviso.idSolicitud);
        if (!StringUtils.hasText(correo)) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "La solicitud no tiene un correo disponible.");
        }
        if (!notificacionService.enviar(aviso.idSolicitud, correo, aviso.mensaje.trim())) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de correo no pudo enviar el aviso.");
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> error(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(Collections.singletonMap("mensaje", mensaje));
    }

    public static class Aviso {
        public Long idSolicitud;
        public String mensaje;
    }
}
