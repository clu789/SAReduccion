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
import org.springframework.web.bind.annotation.RestController;

import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.model.ConvocatoriaDisponible;
import com.itt.sareduccion.model.DeduccionPropia;
import com.itt.sareduccion.model.HistorialPropio;
import com.itt.sareduccion.model.IdentidadSolicitante;
import com.itt.sareduccion.model.PerfilSolicitante;
import com.itt.sareduccion.model.SolicitudPropia;
import com.itt.sareduccion.service.ReglaNegocioException;
import com.itt.sareduccion.service.SolicitanteService;
import com.itt.sareduccion.service.SolicitanteService.Seguimiento;
import com.itt.sareduccion.service.SolicitudNoEncontradaException;

/**
 * Panel del estudiante y aspirante (equipo 3). Solo atiende sesiones con rol
 * STUDENT o APPLICANT; la identidad se toma de la sesion y nunca de la ruta ni
 * del cuerpo. Las operaciones de cambio exigen el token CSRF de la sesion.
 */
@RestController
@RequestMapping("/api/solicitante")
public class SolicitanteController {

    private static final String CSRF_HEADER = SessionAttributes.CSRF_HEADER;

    private final SolicitanteService solicitanteService;

    public SolicitanteController(SolicitanteService solicitanteService) {
        this.solicitanteService = solicitanteService;
    }

    @GetMapping("/perfil")
    public ResponseEntity<?> perfil(HttpServletRequest request) {
        IdentidadSolicitante identidad = solicitante(request);
        if (identidad == null) {
            return noAutorizado();
        }
        return ResponseEntity.ok(perfilJson(solicitanteService.perfil(identidad)));
    }

    @GetMapping("/convocatorias")
    public ResponseEntity<?> convocatorias(HttpServletRequest request) {
        IdentidadSolicitante identidad = solicitante(request);
        if (identidad == null) {
            return noAutorizado();
        }
        List<Map<String, Object>> respuesta = new ArrayList<Map<String, Object>>();
        for (ConvocatoriaDisponible convocatoria : solicitanteService.convocatorias(identidad)) {
            respuesta.add(convocatoriaJson(convocatoria));
        }
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/solicitudes")
    public ResponseEntity<?> solicitudes(HttpServletRequest request) {
        IdentidadSolicitante identidad = solicitante(request);
        if (identidad == null) {
            return noAutorizado();
        }
        List<Map<String, Object>> respuesta = new ArrayList<Map<String, Object>>();
        for (Seguimiento seguimiento : solicitanteService.seguimiento(identidad)) {
            respuesta.add(seguimientoJson(seguimiento));
        }
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/solicitudes/{id}")
    public ResponseEntity<?> solicitud(@PathVariable("id") long id, HttpServletRequest request) {
        IdentidadSolicitante identidad = solicitante(request);
        if (identidad == null) {
            return noAutorizado();
        }
        return ResponseEntity.ok(seguimientoJson(solicitanteService.seguimiento(identidad, id)));
    }

    @PutMapping("/solicitudes/{id}/retirar")
    public ResponseEntity<?> retirar(@PathVariable("id") long id,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        IdentidadSolicitante identidad = solicitante(request);
        if (identidad == null) {
            return noAutorizado();
        }
        if (!SessionAttributes.validCsrf(request.getSession(false), csrfHeader)) {
            return csrfInvalido();
        }
        solicitanteService.retirar(identidad, id);
        return ResponseEntity.ok(mensaje("Solicitud retirada correctamente."));
    }

    @PutMapping("/solicitudes/{id}/reenviar")
    public ResponseEntity<?> reenviar(@PathVariable("id") long id,
            @RequestBody(required = false) SolicitanteService.Reenvio body,
            @RequestHeader(value = CSRF_HEADER, required = false) String csrfHeader,
            HttpServletRequest request) {
        IdentidadSolicitante identidad = solicitante(request);
        if (identidad == null) {
            return noAutorizado();
        }
        if (!SessionAttributes.validCsrf(request.getSession(false), csrfHeader)) {
            return csrfInvalido();
        }
        solicitanteService.reenviar(identidad, id, body);
        return ResponseEntity.ok(mensaje("Solicitud reenviada correctamente."));
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> reglaNegocio(ReglaNegocioException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), "REGLA_NEGOCIO"));
    }

    @ExceptionHandler(SolicitudNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> noEncontrada(SolicitudNoEncontradaException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(exception.getMessage(), "NO_ENCONTRADA"));
    }

    /** Identidad del estudiante o aspirante de la sesion, o {@code null} si no hay una valida. */
    private IdentidadSolicitante solicitante(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return IdentidadSolicitante.desde(SessionAttributes.principal(session));
    }

    private ResponseEntity<ErrorResponse> noAutorizado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("La sesión expiró o no tiene acceso al panel del solicitante",
                        "SESION_NO_VALIDA"));
    }

    private ResponseEntity<ErrorResponse> csrfInvalido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Solicitud no válida", "CSRF_INVALIDO"));
    }

    private Map<String, Object> mensaje(String texto) {
        Map<String, Object> respuesta = new LinkedHashMap<String, Object>();
        respuesta.put("mensaje", texto);
        return respuesta;
    }

    private Map<String, Object> perfilJson(PerfilSolicitante perfil) {
        Map<String, Object> fila = new LinkedHashMap<String, Object>();
        fila.put("tipo", perfil.getTipo());
        fila.put("identificador", perfil.getIdentificador());
        fila.put("nombre", perfil.getNombre());
        fila.put("primerApellido", perfil.getPrimerApellido());
        fila.put("segundoApellido", perfil.getSegundoApellido());
        fila.put("nombreCompleto", perfil.getNombreCompleto());
        fila.put("carrera", perfil.getCarrera());
        fila.put("semestre", perfil.getSemestre());
        fila.put("periodo", perfil.getPeriodo());
        fila.put("periodoDescripcion", perfil.getPeriodoDescripcion());
        fila.put("anio", perfil.getAnio());
        return fila;
    }

    private Map<String, Object> convocatoriaJson(ConvocatoriaDisponible convocatoria) {
        Map<String, Object> fila = new LinkedHashMap<String, Object>();
        fila.put("idConvocatoria", convocatoria.getIdConvocatoria());
        fila.put("periodo", convocatoria.getPeriodo());
        fila.put("periodoDescripcion", convocatoria.getPeriodoDescripcion());
        fila.put("anio", convocatoria.getAnio());
        fila.put("fechaInicio", convocatoria.getFechaInicio());
        fila.put("fechaFin", convocatoria.getFechaFin());
        fila.put("idTipoSolicitud", convocatoria.getIdTipoSolicitud());
        fila.put("tipoSolicitud", convocatoria.getTipoNombre());
        fila.put("tipoDescripcion", convocatoria.getTipoDescripcion());
        fila.put("idFormulario", convocatoria.getIdFormulario());
        fila.put("formulario", convocatoria.getFormularioNombre());
        fila.put("montoDeduccion", convocatoria.getMontoDeduccion());
        fila.put("idSolicitudExistente", convocatoria.getIdSolicitudExistente());
        fila.put("puedeIniciar", convocatoria.getIdSolicitudExistente() == null);
        return fila;
    }

    private Map<String, Object> seguimientoJson(Seguimiento seguimiento) {
        SolicitudPropia solicitud = seguimiento.getSolicitud();
        Map<String, Object> fila = new LinkedHashMap<String, Object>();
        fila.put("idSolicitud", solicitud.getIdSolicitud());
        fila.put("idConvocatoria", solicitud.getIdConvocatoria());
        fila.put("periodo", solicitud.getPeriodo());
        fila.put("periodoDescripcion", solicitud.getPeriodoDescripcion());
        fila.put("anio", solicitud.getAnio());
        fila.put("idTipoSolicitud", solicitud.getIdTipoSolicitud());
        fila.put("tipoSolicitud", solicitud.getTipoNombre());
        fila.put("idFormulario", solicitud.getIdFormulario());
        fila.put("idEstatus", solicitud.getIdEstatus());
        fila.put("estatus", solicitud.getEstatusNombre());
        fila.put("fechaSolicitud", solicitud.getFechaSolicitud());
        fila.put("fechaActualizacion", solicitud.getFechaActualizacion());
        fila.put("fechaInicioRecepcion", solicitud.getFechaInicioRecepcion());
        fila.put("fechaFinRecepcion", solicitud.getFechaFinRecepcion());
        fila.put("recepcionAbierta", solicitud.isRecepcionAbierta());
        fila.put("puedeRetirar", seguimiento.isPuedeRetirar());
        fila.put("puedeReenviar", seguimiento.isPuedeReenviar());

        List<Map<String, Object>> historial = new ArrayList<Map<String, Object>>();
        for (HistorialPropio item : seguimiento.getHistorial()) {
            Map<String, Object> movimiento = new LinkedHashMap<String, Object>();
            movimiento.put("idEstatus", item.getIdEstatus());
            movimiento.put("estatus", item.getEstatusNombre());
            movimiento.put("comentario", item.getComentario());
            movimiento.put("administrativo", item.isAdministrativo());
            movimiento.put("fecha", item.getFecha());
            historial.add(movimiento);
        }
        fila.put("historial", historial);

        DeduccionPropia deduccion = seguimiento.getDeduccion();
        if (deduccion == null) {
            fila.put("deduccion", null);
        } else {
            Map<String, Object> datos = new LinkedHashMap<String, Object>();
            datos.put("idDeduccion", deduccion.getIdDeduccion());
            datos.put("idEstatus", deduccion.getIdEstatus());
            datos.put("estatus", solicitanteService.nombreEstatusDeduccion(deduccion.getIdEstatus()));
            datos.put("montoPago", deduccion.getMontoPago());
            datos.put("montoDeduccion", deduccion.getMontoDeduccion());
            datos.put("montoFinal", deduccion.getMontoFinal());
            fila.put("deduccion", datos);
        }
        return fila;
    }

    public static final class ErrorResponse {
        private final String error;
        private final String codigo;

        private ErrorResponse(String error, String codigo) {
            this.error = error;
            this.codigo = codigo;
        }

        public String getError() {
            return error;
        }

        public String getCodigo() {
            return codigo;
        }
    }
}
