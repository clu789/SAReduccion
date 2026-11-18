package com.itt.sareduccion.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Convocatoria en periodo de recepcion junto con un tipo de solicitud
 * configurado. {@code idSolicitudExistente} indica si el solicitante ya tiene
 * una solicitud en esa convocatoria (solo se permite una).
 */
public final class ConvocatoriaDisponible {

    private final Long idConvocatoria;
    private final Integer periodo;
    private final String periodoDescripcion;
    private final Integer anio;
    private final LocalDate fechaInicio;
    private final LocalDate fechaFin;
    private final Integer idTipoSolicitud;
    private final String tipoNombre;
    private final String tipoDescripcion;
    private final Long idFormulario;
    private final String formularioNombre;
    private final BigDecimal montoDeduccion;
    private final Long idSolicitudExistente;

    public ConvocatoriaDisponible(Long idConvocatoria, Integer periodo, String periodoDescripcion, Integer anio,
            LocalDate fechaInicio, LocalDate fechaFin, Integer idTipoSolicitud, String tipoNombre,
            String tipoDescripcion, Long idFormulario, String formularioNombre, BigDecimal montoDeduccion,
            Long idSolicitudExistente) {
        this.idConvocatoria = idConvocatoria;
        this.periodo = periodo;
        this.periodoDescripcion = periodoDescripcion;
        this.anio = anio;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.idTipoSolicitud = idTipoSolicitud;
        this.tipoNombre = tipoNombre;
        this.tipoDescripcion = tipoDescripcion;
        this.idFormulario = idFormulario;
        this.formularioNombre = formularioNombre;
        this.montoDeduccion = montoDeduccion;
        this.idSolicitudExistente = idSolicitudExistente;
    }

    public Long getIdConvocatoria() {
        return idConvocatoria;
    }

    public Integer getPeriodo() {
        return periodo;
    }

    public String getPeriodoDescripcion() {
        return periodoDescripcion;
    }

    public Integer getAnio() {
        return anio;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public Integer getIdTipoSolicitud() {
        return idTipoSolicitud;
    }

    public String getTipoNombre() {
        return tipoNombre;
    }

    public String getTipoDescripcion() {
        return tipoDescripcion;
    }

    public Long getIdFormulario() {
        return idFormulario;
    }

    public String getFormularioNombre() {
        return formularioNombre;
    }

    public BigDecimal getMontoDeduccion() {
        return montoDeduccion;
    }

    public Long getIdSolicitudExistente() {
        return idSolicitudExistente;
    }
}
