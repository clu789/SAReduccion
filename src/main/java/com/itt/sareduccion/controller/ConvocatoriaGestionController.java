
package com.itt.sareduccion.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.itt.sareduccion.auth.AuthRole;
import com.itt.sareduccion.auth.SessionAttributes;
import com.itt.sareduccion.auth.SessionPrincipal;
import com.itt.sareduccion.service.ConvocatoriaGestionService;
import com.itt.sareduccion.service.ReglaNegocioException;

@RestController
@RequestMapping("/api/admin/convocatorias")
public class ConvocatoriaGestionController {

    private final ConvocatoriaGestionService service;

    public ConvocatoriaGestionController(
            ConvocatoriaGestionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> listarConvocatorias(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        SessionPrincipal principal =
                SessionAttributes.principal(session);

        if (principal == null
                || principal.getRole() != AuthRole.ADMIN) {
            return error(
                    HttpStatus.UNAUTHORIZED,
                    "Acceso no autorizado.");
        }

        List<Map<String, Object>> convocatorias =
                service.listarConvocatorias(principal.getIdentity());

        Map<String, Object> respuesta =
                new LinkedHashMap<String, Object>();

        respuesta.put("convocatorias", convocatorias);

        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> consultarConvocatoria(
            @PathVariable("id") Long idConvocatoria,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        SessionPrincipal principal =
                SessionAttributes.principal(session);

        if (principal == null
                || principal.getRole() != AuthRole.ADMIN) {
            return error(
                    HttpStatus.UNAUTHORIZED,
                    "Acceso no autorizado.");
        }

        if (idConvocatoria == null || idConvocatoria <= 0) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "El identificador de la convocatoria no es valido.");
        }

        Map<String, Object> convocatoria =
                service.consultarConvocatoria(
                        principal.getIdentity(),
                        idConvocatoria);

        if (convocatoria == null) {
            return error(
                    HttpStatus.NOT_FOUND,
                    "No se encontro la convocatoria solicitada.");
        }

        return ResponseEntity.ok(convocatoria);
    }

    @PostMapping
    public ResponseEntity<?> crearConvocatoria(
            @RequestBody(required = false) CrearConvocatoriaRequest body,
            @RequestHeader(
                    value = SessionAttributes.CSRF_HEADER,
                    required = false) String csrfHeader,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return error(
                    HttpStatus.FORBIDDEN,
                    "Token de seguridad invalido.");
        }

        SessionPrincipal principal =
                SessionAttributes.principal(session);

        if (principal == null
                || principal.getRole() != AuthRole.ADMIN) {
            return error(
                    HttpStatus.UNAUTHORIZED,
                    "Acceso no autorizado.");
        }

        if (body == null) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "Debe proporcionar los datos de la convocatoria.");
        }

        Long idGenerado = service.crearConvocatoria(
                principal.getIdentity(),
                body.getPeriodo(),
                body.getAnio(),
                body.getFechaInicio(),
                body.getFechaFin(),
                body.getFechaLimiteAutorizacion(),
                body.getFechaLimiteAplicacion(),
                body.getMontoTrabajador(),
                body.getMontoHomologado());

        Map<String, Object> respuesta =
                new LinkedHashMap<String, Object>();

        respuesta.put("mensaje",
                "Convocatoria creada correctamente.");
        respuesta.put("idConvocatoria", idGenerado);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(respuesta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarConvocatoria(
            @PathVariable("id") Long idConvocatoria,
            @RequestBody(required = false) ActualizarConvocatoriaRequest body,
            @RequestHeader(
                    value = SessionAttributes.CSRF_HEADER,
                    required = false) String csrfHeader,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (!SessionAttributes.validCsrf(session, csrfHeader)) {
            return error(HttpStatus.FORBIDDEN, "Token de seguridad invalido.");
        }

        SessionPrincipal principal = SessionAttributes.principal(session);
        if (principal == null || principal.getRole() != AuthRole.ADMIN) {
            return error(HttpStatus.UNAUTHORIZED, "Acceso no autorizado.");
        }

        if (idConvocatoria == null || idConvocatoria <= 0) {
            return error(HttpStatus.BAD_REQUEST,
                    "El identificador de la convocatoria no es valido.");
        }
        if (body == null) {
            return error(HttpStatus.BAD_REQUEST,
                    "Debe proporcionar los datos de la convocatoria.");
        }

        Map<String, Object> resultado = service.actualizarConvocatoria(
                principal.getIdentity(),
                idConvocatoria,
                body.getFechaInicio(),
                body.getFechaFin(),
                body.getFechaLimiteAutorizacion(),
                body.getFechaLimiteAplicacion(),
                body.getMontoTrabajador(),
                body.getMontoHomologado());

        Map<String, Object> respuesta = new LinkedHashMap<String, Object>();
        respuesta.put("mensaje", Boolean.TRUE.equals(resultado.get("huboCambios"))
                ? "Convocatoria actualizada correctamente."
                : "No se detectaron cambios en la convocatoria.");
        respuesta.put("idConvocatoria", idConvocatoria);
        respuesta.putAll(resultado);
        return ResponseEntity.ok(respuesta);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<?> manejarReglaNegocio(
            ReglaNegocioException exception) {

        return error(
                HttpStatus.CONFLICT,
                exception.getMessage());
    }

    private ResponseEntity<?> error(
            HttpStatus estado,
            String mensaje) {

        Map<String, Object> respuesta =
                new LinkedHashMap<String, Object>();

        respuesta.put("error", mensaje);

        return ResponseEntity
                .status(estado)
                .body(respuesta);
    }

    public static class CrearConvocatoriaRequest {

        private Integer periodo;
        private Integer anio;

        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private LocalDate fechaLimiteAutorizacion;
        private LocalDate fechaLimiteAplicacion;

        private BigDecimal montoTrabajador;
        private BigDecimal montoHomologado;

        public Integer getPeriodo() {
            return periodo;
        }

        public void setPeriodo(Integer periodo) {
            this.periodo = periodo;
        }

        public Integer getAnio() {
            return anio;
        }

        public void setAnio(Integer anio) {
            this.anio = anio;
        }

        public LocalDate getFechaInicio() {
            return fechaInicio;
        }

        public void setFechaInicio(LocalDate fechaInicio) {
            this.fechaInicio = fechaInicio;
        }

        public LocalDate getFechaFin() {
            return fechaFin;
        }

        public void setFechaFin(LocalDate fechaFin) {
            this.fechaFin = fechaFin;
        }

        public LocalDate getFechaLimiteAutorizacion() {
            return fechaLimiteAutorizacion;
        }

        public void setFechaLimiteAutorizacion(
                LocalDate fechaLimiteAutorizacion) {
            this.fechaLimiteAutorizacion =
                    fechaLimiteAutorizacion;
        }

        public LocalDate getFechaLimiteAplicacion() {
            return fechaLimiteAplicacion;
        }

        public void setFechaLimiteAplicacion(
                LocalDate fechaLimiteAplicacion) {
            this.fechaLimiteAplicacion =
                    fechaLimiteAplicacion;
        }

        public BigDecimal getMontoTrabajador() {
            return montoTrabajador;
        }

        public void setMontoTrabajador(
                BigDecimal montoTrabajador) {
            this.montoTrabajador = montoTrabajador;
        }

        public BigDecimal getMontoHomologado() {
            return montoHomologado;
        }

        public void setMontoHomologado(
                BigDecimal montoHomologado) {
            this.montoHomologado = montoHomologado;
        }
    }

    public static class ActualizarConvocatoriaRequest {
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private LocalDate fechaLimiteAutorizacion;
        private LocalDate fechaLimiteAplicacion;
        private BigDecimal montoTrabajador;
        private BigDecimal montoHomologado;

        public LocalDate getFechaInicio() { return fechaInicio; }
        public void setFechaInicio(LocalDate value) { this.fechaInicio = value; }
        public LocalDate getFechaFin() { return fechaFin; }
        public void setFechaFin(LocalDate value) { this.fechaFin = value; }
        public LocalDate getFechaLimiteAutorizacion() { return fechaLimiteAutorizacion; }
        public void setFechaLimiteAutorizacion(LocalDate value) { this.fechaLimiteAutorizacion = value; }
        public LocalDate getFechaLimiteAplicacion() { return fechaLimiteAplicacion; }
        public void setFechaLimiteAplicacion(LocalDate value) { this.fechaLimiteAplicacion = value; }
        public BigDecimal getMontoTrabajador() { return montoTrabajador; }
        public void setMontoTrabajador(BigDecimal value) { this.montoTrabajador = value; }
        public BigDecimal getMontoHomologado() { return montoHomologado; }
        public void setMontoHomologado(BigDecimal value) { this.montoHomologado = value; }
    }

}
