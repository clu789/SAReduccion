package com.itt.sareduccion.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.auth.SessionPrincipal;
import com.itt.sareduccion.model.DeduccionAdmin;
import com.itt.sareduccion.model.RespuestaSolicitud;
import com.itt.sareduccion.model.SolicitudAdmin;
import com.itt.sareduccion.service.ReglaNegocioException;
import com.itt.sareduccion.service.SolicitudAdminService;

/**
 * Panel administrativo (Equipo 2): consulta y resolucion de solicitudes y
 * administracion de deducciones. La identidad del administrador siempre se lee
 * de la sesion autenticada; el rol ADMIN y el token CSRF se validan aqui.
 */
@RestController
@RequestMapping("/api")
public class SolicitudAdminController {

    private static final String CSRF_HEADER = SessionAttributes.CSRF_HEADER;

    private final SolicitudAdminService solicitudAdminService;

    public SolicitudAdminController(SolicitudAdminService solicitudAdminService) {
        this.solicitudAdminService = solicitudAdminService;
    }

    @GetMapping("/solicitudes")
    public ResponseEntity<?> listar(@RequestParam(value = "estatus", required = false) Integer estatus,
            @RequestParam(value = "convocatoria", required = false) Long convocatoria,
            @RequestParam(value = "solicitante", required = false) String solicitante,
            HttpServletRequest request) {
        SessionPrincipal principal = administrador(request);
        if (principal == null) {
            return noAutorizado();
        }
        List<SolicitudAdmin> solicitudes = solicitudAdminService.listar(principal.getIdentity(), estatus,
                convocatoria, solicitante);
        List<SolicitudResumen> respuesta = new ArrayList<SolicitudResumen>();
        for (SolicitudAdmin solicitud : solicitudes) {
            respuesta.add(SolicitudResumen.desde(solicitud, solicitudAdminService));
        }
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/solicitudes/estadisticas")
    public ResponseEntity<?> estadisticas(HttpServletRequest request) {
        SessionPrincipal principal = administrador(request);
        if (principal == null) {
            return noAutorizado();
        }
        return ResponseEntity.ok(solicitudAdminService.estadisticas(principal.getIdentity()));
    }

    @GetMapping("/solicitudes/{id}")
    public ResponseEntity<?> detalle(@PathVariable("id") long id, HttpServletRequest request) {
        SessionPrincipal principal = administrador(request);
        if (principal == null) {
            return noAutorizado();
        }
        SolicitudAdminService.DetalleSolicitud detalle = solicitudAdminService.detalle(principal.getIdentity(), id);

        Map<String, Object> respuesta = new LinkedHashMap<String, Object>();
        SolicitudAdmin solicitud = detalle.getSolicitud();
        respuesta.put("idSolicitud", solicitud.getIdSolicitud());
        respuesta.put("solicitante", solicitud.getSolicitante());
        respuesta.put("nombreSolicitante", solicitud.getSolicitante());
        respuesta.put("numControl", solicitud.getNumControl());
        respuesta.put("ficha", solicitud.getFicha());
        respuesta.put("idConvocatoria", solicitud.getIdConvocatoria());
        respuesta.put("idTipoSolicitud", solicitud.getIdTipoSolicitud());
        respuesta.put("tipoSolicitante", solicitud.getTipoSolicitante());
        respuesta.put("estatus", solicitudAdminService.nombreEstatus(solicitud.getIdEstatusSolicitud()));
        respuesta.put("idEstatus", solicitud.getIdEstatusSolicitud());

        List<Map<String, Object>> respuestas = new ArrayList<Map<String, Object>>();
        for (RespuestaSolicitud item : detalle.getRespuestas()) {
            Map<String, Object> fila = new LinkedHashMap<String, Object>();
            fila.put("idPregunta", item.getIdPregunta());
            fila.put("idOpcion", item.getIdOpcion());
            fila.put("respuesta", item.getTextoRespuesta());
            respuestas.add(fila);
        }
        respuesta.put("respuestas", respuestas);
        respuesta.put("deduccion", deduccionJson(detalle.getDeduccion()));
        // Sin procedimiento de historial en la BD actual (se cubre en la Fase 3).
        respuesta.put("historial", new ArrayList<Object>());
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/deducciones")
    public ResponseEntity<?> listarDeducciones(@RequestParam(value = "estatus", required = false) Integer estatus,
            HttpServletRequest request) {
        SessionPrincipal principal = administrador(request);
        if (principal == null) {
            return noAutorizado();
        }
        List<DeduccionAdmin> deducciones = solicitudAdminService.listarDeducciones(principal.getIdentity(), estatus);
        List<Map<String, Object>> respuesta = new ArrayList<Map<String, Object>>();
        for (DeduccionAdmin deduccion : deducciones) {
            respuesta.add(deduccionJson(deduccion));
        }
        return ResponseEntity.ok(respuesta);
    }

    @PutMapping("/solicitudes/{id}/aprobar")
    public ResponseEntity<?> aprobar(@PathVariable("id") long id,
            @RequestBody(required = false) AprobarRequest body,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return csrfInvalido();
        }
        SessionPrincipal principal = administrador(request);
        if (principal == null) {
            return noAutorizado();
        }
        AprobarRequest datos = body == null ? new AprobarRequest() : body;
        solicitudAdminService.aprobar(principal.getIdentity(), id, datos.getIdMontoInscripcion(),
                datos.getIdMontoReinscripcion(), datos.getComentario());
        return ResponseEntity.ok(mensaje("Solicitud aprobada correctamente."));
    }

    @PutMapping("/solicitudes/{id}/rechazar")
    public ResponseEntity<?> rechazar(@PathVariable("id") long id,
            @RequestBody(required = false) ComentarioRequest body,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        return resolverRechazo(id, body, csrfHeader, request);
    }

    @PutMapping("/solicitudes/{id}/correccion")
    public ResponseEntity<?> correccion(@PathVariable("id") long id,
            @RequestBody(required = false) ComentarioRequest body,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        return resolverRechazo(id, body, csrfHeader, request);
    }

    @PutMapping("/deducciones/{id}/cancelar")
    public ResponseEntity<?> cancelarDeduccion(@PathVariable("id") long id,
            @RequestBody(required = false) ComentarioRequest body,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return csrfInvalido();
        }
        SessionPrincipal principal = administrador(request);
        if (principal == null) {
            return noAutorizado();
        }
        String comentario = body == null ? null : body.getComentario();
        solicitudAdminService.cancelarDeduccion(principal.getIdentity(), id, comentario);
        return ResponseEntity.ok(mensaje("Deduccion cancelada correctamente."));
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> reglaNegocio(ReglaNegocioException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(exception.getMessage()));
    }

    private ResponseEntity<?> resolverRechazo(long id, ComentarioRequest body, String csrfHeader,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return csrfInvalido();
        }
        SessionPrincipal principal = administrador(request);
        if (principal == null) {
            return noAutorizado();
        }
        String comentario = body == null ? null : body.getComentario();
        solicitudAdminService.rechazar(principal.getIdentity(), id, comentario);
        return ResponseEntity.ok(mensaje("Solicitud rechazada correctamente."));
    }

    /** Principal de sesion solo si es un administrador autenticado. */
    private SessionPrincipal administrador(HttpServletRequest request) {
        SessionPrincipal principal = SessionAttributes.principal(request.getSession(false));
        return principal != null && principal.getRole() == AuthRole.ADMIN ? principal : null;
    }

    private ResponseEntity<ErrorResponse> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Acceso no autorizado para el panel administrativo"));
    }

    private ResponseEntity<ErrorResponse> csrfInvalido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Solicitud no válida"));
    }

    private Map<String, Object> mensaje(String texto) {
        Map<String, Object> respuesta = new LinkedHashMap<String, Object>();
        respuesta.put("mensaje", texto);
        return respuesta;
    }

    private Map<String, Object> deduccionJson(DeduccionAdmin deduccion) {
        if (deduccion == null) {
            return null;
        }
        Map<String, Object> fila = new LinkedHashMap<String, Object>();
        fila.put("idDeduccion", deduccion.getIdDeduccion());
        fila.put("idSolicitud", deduccion.getIdSolicitud());
        fila.put("estatus", solicitudAdminService.nombreEstatus(deduccion.getEstatusDeduccion()));
        fila.put("idEstatus", deduccion.getEstatusDeduccion());
        fila.put("montoPago", deduccion.getMontoPago());
        fila.put("montoDeduccion", deduccion.getMontoDeduccion());
        fila.put("montoFinal", deduccion.getMontoFinal());
        return fila;
    }

    public static final class SolicitudResumen {
        private final Long idSolicitud;
        private final String solicitante;
        private final String nombreSolicitante;
        private final String numControl;
        private final String ficha;
        private final Long idConvocatoria;
        private final Integer idTipoSolicitud;
        private final String tipoSolicitante;
        private final String estatus;
        private final Integer idEstatus;

        private SolicitudResumen(SolicitudAdmin solicitud, SolicitudAdminService service) {
            this.idSolicitud = solicitud.getIdSolicitud();
            this.solicitante = solicitud.getSolicitante();
            this.nombreSolicitante = solicitud.getSolicitante();
            this.numControl = solicitud.getNumControl();
            this.ficha = solicitud.getFicha();
            this.idConvocatoria = solicitud.getIdConvocatoria();
            this.idTipoSolicitud = solicitud.getIdTipoSolicitud();
            this.tipoSolicitante = solicitud.getTipoSolicitante();
            this.estatus = service.nombreEstatus(solicitud.getIdEstatusSolicitud());
            this.idEstatus = solicitud.getIdEstatusSolicitud();
        }

        static SolicitudResumen desde(SolicitudAdmin solicitud, SolicitudAdminService service) {
            return new SolicitudResumen(solicitud, service);
        }

        public Long getIdSolicitud() {
            return idSolicitud;
        }

        public String getSolicitante() {
            return solicitante;
        }

        public String getNombreSolicitante() {
            return nombreSolicitante;
        }

        public String getNumControl() {
            return numControl;
        }

        public String getFicha() {
            return ficha;
        }

        public Long getIdConvocatoria() {
            return idConvocatoria;
        }

        public Integer getIdTipoSolicitud() {
            return idTipoSolicitud;
        }

        public String getTipoSolicitante() {
            return tipoSolicitante;
        }

        public String getEstatus() {
            return estatus;
        }

        public Integer getIdEstatus() {
            return idEstatus;
        }
    }

    public static final class AprobarRequest {
        private Long idMontoInscripcion;
        private Long idMontoReinscripcion;
        private String comentario;

        public Long getIdMontoInscripcion() {
            return idMontoInscripcion;
        }

        public void setIdMontoInscripcion(Long idMontoInscripcion) {
            this.idMontoInscripcion = idMontoInscripcion;
        }

        public Long getIdMontoReinscripcion() {
            return idMontoReinscripcion;
        }

        public void setIdMontoReinscripcion(Long idMontoReinscripcion) {
            this.idMontoReinscripcion = idMontoReinscripcion;
        }

        public String getComentario() {
            return comentario;
        }

        public void setComentario(String comentario) {
            this.comentario = comentario;
        }
    }

    public static final class ComentarioRequest {
        private String comentario;

        public String getComentario() {
            return comentario;
        }

        public void setComentario(String comentario) {
            this.comentario = comentario;
        }
    }

    public static final class ErrorResponse {
        private final String error;

        private ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
}
