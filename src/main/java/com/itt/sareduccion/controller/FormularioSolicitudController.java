package com.itt.sareduccion.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.auth.SessionPrincipal;
import com.itt.sareduccion.model.EnvioSolicitudRequest;
import com.itt.sareduccion.service.AccesoDenegadoException;
import com.itt.sareduccion.service.FormularioSolicitudService;
import com.itt.sareduccion.service.ReglaNegocioException;
import com.itt.sareduccion.service.SolicitudNoEncontradaException;
import com.itt.sareduccion.service.ValidacionRespuestasException;

/**
 * Formulario de solicitud para estudiantes y aspirantes (Equipo 5). La
 * identidad se lee siempre de la sesion; los endpoints que modifican datos
 * exigen el token CSRF comun. Sin sesion responde 401; con rol ADMIN, 403.
 */
@RestController
@RequestMapping("/api/solicitante")
public class FormularioSolicitudController {

    private static final Logger LOG = LoggerFactory.getLogger(FormularioSolicitudController.class);
    private static final String CSRF_HEADER = SessionAttributes.CSRF_HEADER;

    private final FormularioSolicitudService service;

    public FormularioSolicitudController(FormularioSolicitudService service) {
        this.service = service;
    }

    @GetMapping("/formulario")
    public ResponseEntity<?> formulario(@RequestParam("convocatoria") Long convocatoria,
            @RequestParam("tipo") Integer tipo, HttpServletRequest request) {
        SessionPrincipal principal = principal(request);
        ResponseEntity<ErrorRespuesta> rechazo = rechazoDeAcceso(principal);
        if (rechazo != null) {
            return rechazo;
        }
        return ResponseEntity.ok(service.obtenerFormulario(principal, convocatoria.longValue(), tipo.intValue()));
    }

    @PostMapping("/solicitudes")
    public ResponseEntity<?> enviar(@RequestBody EnvioSolicitudRequest body,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader, HttpServletRequest request) {
        SessionPrincipal principal = principal(request);
        ResponseEntity<ErrorRespuesta> rechazo = rechazoDeAcceso(principal);
        if (rechazo != null) {
            return rechazo;
        }
        if (!csrfValido(request, csrfHeader)) {
            return csrfInvalido();
        }
        long id = service.enviar(principal, body);
        Map<String, Object> respuesta = new LinkedHashMap<String, Object>();
        respuesta.put("idSolicitud", Long.valueOf(id));
        respuesta.put("estatus", "PENDIENTE");
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @GetMapping("/solicitudes/{id}/respuestas")
    public ResponseEntity<?> respuestas(@PathVariable("id") Long id, HttpServletRequest request) {
        SessionPrincipal principal = principal(request);
        ResponseEntity<ErrorRespuesta> rechazo = rechazoDeAcceso(principal);
        if (rechazo != null) {
            return rechazo;
        }
        return ResponseEntity.ok(service.misRespuestas(principal, id.longValue()));
    }

    @GetMapping("/solicitudes/{id}/reenvio")
    public ResponseEntity<?> prepararReenvio(@PathVariable("id") Long id, HttpServletRequest request) {
        SessionPrincipal principal = principal(request);
        ResponseEntity<ErrorRespuesta> rechazo = rechazoDeAcceso(principal);
        if (rechazo != null) {
            return rechazo;
        }
        return ResponseEntity.ok(service.prepararReenvio(principal, id.longValue()));
    }

    @PutMapping("/solicitudes/{id}/reenvio")
    public ResponseEntity<?> reenviar(@PathVariable("id") Long id, @RequestBody EnvioSolicitudRequest body,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader, HttpServletRequest request) {
        SessionPrincipal principal = principal(request);
        ResponseEntity<ErrorRespuesta> rechazo = rechazoDeAcceso(principal);
        if (rechazo != null) {
            return rechazo;
        }
        if (!csrfValido(request, csrfHeader)) {
            return csrfInvalido();
        }
        service.reenviar(principal, id.longValue(), body);
        Map<String, Object> respuesta = new LinkedHashMap<String, Object>();
        respuesta.put("idSolicitud", id);
        respuesta.put("estatus", "PENDIENTE");
        return ResponseEntity.ok(respuesta);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorRespuesta> reglaNegocio(ReglaNegocioException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorRespuesta(exception.getMessage(), null));
    }

    @ExceptionHandler(ValidacionRespuestasException.class)
    public ResponseEntity<ErrorRespuesta> validacion(ValidacionRespuestasException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorRespuesta(exception.getMessage(), exception.getErrores()));
    }

    @ExceptionHandler(SolicitudNoEncontradaException.class)
    public ResponseEntity<ErrorRespuesta> noEncontrada(SolicitudNoEncontradaException exception) {
        LOG.warn("ACCESO_DENEGADO solicitud no encontrada o ajena");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorRespuesta("Solicitud no encontrada", null));
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorRespuesta> accesoDenegado(AccesoDenegadoException exception) {
        LOG.warn("ACCESO_DENEGADO perfil no valido para el formulario");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorRespuesta("Acceso no permitido", null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> cuerpoInvalido(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorRespuesta("La solicitud tiene un formato no válido", null));
    }

    private SessionPrincipal principal(HttpServletRequest request) {
        return SessionAttributes.principal(request.getSession(false));
    }

    /** 401 sin sesion; 403 si el rol no es de solicitante (por ejemplo, un administrador). */
    private ResponseEntity<ErrorRespuesta> rechazoDeAcceso(SessionPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorRespuesta("Su sesión no es válida o expiró. Inicie sesión de nuevo.", null));
        }
        if (principal.getRole() != AuthRole.STUDENT && principal.getRole() != AuthRole.APPLICANT) {
            LOG.warn("ACCESO_DENEGADO rol={}", principal.getRole());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorRespuesta("Acceso no permitido", null));
        }
        return null;
    }

    private boolean csrfValido(HttpServletRequest request, String csrfHeader) {
        HttpSession session = request.getSession(false);
        return SessionAttributes.validCsrf(session, csrfHeader);
    }

    private ResponseEntity<?> csrfInvalido() {
        LOG.warn("ACCESO_DENEGADO token CSRF invalido");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorRespuesta("Solicitud no válida", null));
    }

    /** Cuerpo de error: mensaje y, para HTTP 400, un error por pregunta (lista vacia si no aplica). */
    public static final class ErrorRespuesta {
        private final String error;
        private final List<ValidacionRespuestasException.ErrorCampo> errores;

        private ErrorRespuesta(String error, List<ValidacionRespuestasException.ErrorCampo> errores) {
            this.error = error;
            this.errores = errores == null
                    ? new ArrayList<ValidacionRespuestasException.ErrorCampo>() : errores;
        }

        public String getError() {
            return error;
        }

        public List<ValidacionRespuestasException.ErrorCampo> getErrores() {
            return errores;
        }
    }
}
